package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 修改个人资料入参。
 * <p>注意这里<b>没有</b> username、password、status ——
 * 用户名不允许改（会影响历史数据关联），密码走单独的改密接口。</p>
 */
@Data
public class UpdateUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 20, message = "昵称最长 20 个字符")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String email;

    private String avatar;
}
