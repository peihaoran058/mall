package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 修改密码入参。
 *
 * <p><b>课上重点一：为什么必须传原密码？</b><br>
 * 不校验原密码的话，只要拿到一个有效的登录 token（比如用户在网吧忘记退出），
 * 攻击者就能直接改掉密码，把账号彻底据为己有。
 * 要求输入原密码，等于把"改密"这个高危操作重新拉回到"必须知道密码"的门槛上。</p>
 *
 * <p><b>课上重点二：新密码不能和原密码相同。</b><br>
 * 看起来是废话，但很多系统真的不做这个校验，
 * 导致用户在"被要求改密"的场景下输入一模一样的密码就通过了。</p>
 */
@Data
public class UpdatePasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度必须在 6~20 位之间")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
