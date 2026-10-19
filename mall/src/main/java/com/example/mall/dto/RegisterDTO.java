package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 注册入参。
 *
 * <p><b>课上重点：为什么用 DTO 而不是直接用 {@code SysUser} 实体接收参数？</b></p>
 * <ol>
 *     <li><b>安全</b>：实体接收参数，前端可以偷偷多传 {@code id=1}、
 *         {@code status=1}、{@code deleted=0}，产生"参数覆盖"漏洞。
 *         DTO 里没有的字段，前端传了也进不来。</li>
 *     <li><b>校验分组</b>：注册要校验 confirmPassword，登录不需要，
 *         用同一个实体会互相干扰。</li>
 *     <li><b>解耦</b>：表加字段不影响接口契约。</li>
 * </ol>
 */
@Data
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度必须在 4~20 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在 6~20 位之间")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    @Size(max = 20, message = "昵称最长 20 个字符")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String email;
}
