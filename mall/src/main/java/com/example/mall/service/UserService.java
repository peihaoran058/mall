package com.example.mall.service;

import com.example.mall.dto.LoginDTO;
import com.example.mall.dto.RegisterDTO;
import com.example.mall.dto.UpdatePasswordDTO;
import com.example.mall.dto.UpdateUserDTO;
import com.example.mall.entity.SysUser;
import com.example.mall.vo.LoginVO;

import javax.servlet.http.HttpSession;

/**
 * 用户服务（第3、4课时）。
 *
 * <p>接口 + 实现类分开写，不是为了"显得规范"，而是：
 * 事务/缓存这些 AOP 增强是基于**接口代理（JDK 动态代理）**生效的，
 * 另外 Controller 依赖接口也方便以后换实现（比如接第三方登录）。</p>
 */
public interface UserService {

    /** 注册 */
    void register(RegisterDTO dto);

    /** 登录：写 Redis token + 写 Session，返回 token */
    LoginVO login(LoginDTO dto);

    /** 获取当前登录用户信息 */
    SysUser getInfo(Long userId);

    /** 修改个人资料 */
    SysUser updateInfo(Long userId, UpdateUserDTO dto);

    /** 修改密码：必须校验原密码 */
    void updatePassword(Long userId, UpdatePasswordDTO dto);

    /** 退出登录：清 Redis token 和 Session */
    void logout(String token);
}
