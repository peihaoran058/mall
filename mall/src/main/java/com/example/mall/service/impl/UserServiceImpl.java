package com.example.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mall.common.Constants;
import com.example.mall.common.ResultCode;
import com.example.mall.dto.LoginDTO;
import com.example.mall.dto.RegisterDTO;
import com.example.mall.dto.UpdatePasswordDTO;
import com.example.mall.dto.UpdateUserDTO;
import com.example.mall.entity.SysUser;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.SysUserMapper;
import com.example.mall.service.UserService;
import com.example.mall.util.MD5Util;
import com.example.mall.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpSession;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        if(dto == null){
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        // 1. 业务校验：两次密码是否一致（这个校验用注解做不了，因为要跨字段）
        if (!dto.getPassword().equals(dto.getConfirmPassword())){
            throw new BusinessException(ResultCode.PASSWORD_NOT_MATCH, "两次密码不一致");
        }

        // 2. 用户名重复校验
        Long l = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (l > 0){
            throw new BusinessException(ResultCode.USERNAME_EXISTS, "用户名已存在");
        }

        // 3. 落库
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        // ★ 只存密文，明文密码不落库、不打日志
        user.setPassword(MD5Util.md5(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        sysUserMapper.insert(user);
        log.info("用户注册成功 | id={} | username={}", user.getId(), user.getUsername());
    }

    @Override
    public LoginVO login(LoginDTO dto) {

        if (dto == null){
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        // ★ 安全细节：用户不存在和密码错误返回**同一个**错误提示，
        //   否则攻击者可以用"用户不存在"这个提示来枚举系统里有哪些账号。
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !user.getPassword().equals(MD5Util.md5(dto.getPassword()))){
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        // 推荐，集群可用：生成 token 放 Redis
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(Constants.REDIS_LOGIN_USER + token, user, Constants.LOGIN_TOKEN_TTL, TimeUnit.SECONDS);

        //redisTemplate.opsForValue().set("test:redis:demo", "hello", 30, TimeUnit.SECONDS);

        log.info("用户登录成功 | id={} | username={} | token={}", user.getId(), user.getUsername(), token);

        return LoginVO.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(Constants.ROLE_USER)
                .build();
    }

    @Override
    public SysUser getInfo(Long userId) {
        // 否则用户改了昵称，自己看还是旧的（这是一个很常见的"感觉有 bug"的问题）
        SysUser user = sysUserMapper.selectById(userId);
        if(user == null){
            throw new BusinessException(ResultCode.USER_NOT_EXISTS);
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser updateInfo(Long userId, UpdateUserDTO dto) {

        // ★ 只允许改这几个字段。绝对不要把前端传的对象整个 updateById，
        //   否则用户传 status=1、password=xxx 就能提权或改别人密码。

        SysUser user = new SysUser();
        user.setId(userId);
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        sysUserMapper.updateById(user);
        log.info("用户信息修改成功 | id={} | username={}", userId, user.getUsername());
        return sysUserMapper.selectById(userId);
    }

    /**
     * 修改密码。
     *
     * <p><b>课上重点一：旧密码必须验，而且要和登录一样"模糊报错"。</b></p>
     * <p>如果这里抛出"原密码错误"，攻击者拿到一个有效的登录 token 后，
     * 就可以用"改密接口"当密码爆破器 —— 而登录接口通常有风控、限流，
     * 改密接口往往没有。所以这类接口要么同样加限流，要么至少别给出
     * 过于精确的提示。</p>
     *
     * <p><b>课上重点二：改完密码，旧 token 还在 Redis 里活着。</b></p>
     * <p>这是很多系统真实存在的漏洞：用户改了密码以为把别人踢下线了，
     * 实际上对方手上的 token 到 TTL 到期前一直有效。</p>
     * <p>彻底解决需要"能按用户维度失效 token"，三种常见做法：</p>
     * <ol>
     *     <li>维护 {@code user:token} 集合（用户 → 该用户所有 token），改密时全删</li>
     *     <li>用户表加 {@code password_version} 字段，token 里带上版本号，
     *         校验时比对（推荐，改动最小、无需遍历）</li>
     *     <li>把 token 的 TTL 设得很短（比如 30 分钟）+ 配合 refresh token</li>
     * </ol>
     * <p>本项目为教学简洁，只做了"改密 + 清 Session"，Redis token 的
     * 主动失效留给课堂作为扩展练习。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        // 1. 校验原密码
        SysUser sysUser = sysUserMapper.selectById(userId);
        if (!sysUser.getPassword().equals(MD5Util.md5(dto.getOldPassword()))){
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "用户名或密码错误");
        }

        // 2. 两次新密码是否一致（跨字段校验，注解做不了，必须在 Service 里做）
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())){
            throw new BusinessException(ResultCode.PASSWORD_NOT_MATCH, "新密码不一致");
        }

        // 3. 新旧不能相同
        if (sysUser.getPassword().equals(MD5Util.md5(dto.getNewPassword()))){
            throw new BusinessException(ResultCode.PASSWORD_NOT_CHANGED, "新密码不能和原密码相同");
        }

        // 4. 更新密文。★ 任何情况下都不要把明文密码写进日志
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(MD5Util.md5(dto.getNewPassword()));
        update.setPasswordVersion(sysUser.getPasswordVersion()+1);
        sysUserMapper.updateById(update);

        log.info("用户修改密码成功 | id={}", userId);
    }

    @Override
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            redisTemplate.delete(Constants.REDIS_LOGIN_USER + token);
        }
    }
}
