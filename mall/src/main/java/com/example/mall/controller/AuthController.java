package com.example.mall.controller;

import com.example.mall.common.Constants;
import com.example.mall.common.Result;
import com.example.mall.dto.LoginDTO;
import com.example.mall.dto.RegisterDTO;
import com.example.mall.dto.UpdatePasswordDTO;
import com.example.mall.dto.UpdateUserDTO;
import com.example.mall.entity.SysUser;
import com.example.mall.service.UserService;
import com.example.mall.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;

/**
 * 用户认证与个人中心（第3、4课时）。
 *
 * <p><b>Controller 的职责边界（第3课时强调的规范）：</b></p>
 * <ul>
 *     <li>只做：参数接收、参数校验、调用 Service、包装返回值</li>
 *     <li>不做：业务判断、SQL、事务</li>
 * </ul>
 * <p>判断标准很简单：<b>Controller 里出现 if-else 的业务判断，多半就写错地方了。</b>
 * 业务代码放在 Service，才能被其他 Service 复用（比如"下单"也要校验库存），
 * 也才能在事务里被正确代理。</p>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 注册。
     * <p>{@code @Valid} 触发 DTO 上的校验注解，校验失败会被
     * {@code GlobalExceptionHandler#handleValidException} 捕获。</p>
     */
    @PostMapping("/register")
    public Result<Long> register(@RequestBody @Valid RegisterDTO dto) {
        userService.register(dto);
        return Result.success("注册成功", null);
    }

    /**
     * 登录。
     * <p>返回值里的 token 要放在后续请求的 header 里：
     * {@code token: xxxxxxxx}</p>
     *
     * <p>同时把用户写进了 Session（第4课时方案），并且会把 JSESSIONID 通过
     * Set-Cookie 返回给浏览器 —— 两种鉴权方式都能用。</p>
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = Constants.HEADER_TOKEN, required = false) String token) {
        userService.logout(token);
        return Result.success("已退出登录", null);
    }

    /**
     * 获取当前登录用户信息。
     * <p>{@code @RequestAttribute} 取的是拦截器放进去的 userId，
     * <b>而不是</b>前端传的参数 —— 前端传什么都不能用来查别人。</p>
     */
    @GetMapping("/info")
    public Result<SysUser> info(@RequestAttribute(Constants.REQ_USER_ID) Long userId) {
        return Result.success(userService.getInfo(userId));
    }

    /** 修改个人资料 */
    @PutMapping("/info")
    public Result<SysUser> updateInfo(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                     @RequestBody @Validated UpdateUserDTO dto) {
        return Result.success("修改成功", userService.updateInfo(userId, dto));
    }

    /**
     * 修改密码。
     * <p>注意 userId 依然来自拦截器，而不是请求参数 ——
     * 否则就是"能改别人密码"的严重漏洞。</p>
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                      @RequestBody @Valid UpdatePasswordDTO dto) {
        userService.updatePassword(userId, dto);
        return Result.success("密码修改成功", null);
    }
}
