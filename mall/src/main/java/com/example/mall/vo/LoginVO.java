package com.example.mall.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录成功返回。
 *
 * <p>密码、盐值等敏感信息<b>绝不出现在返回体里</b>。
 * 只返回前端需要的最小集合。</p>
 */
@Data
@Builder
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录令牌，后续请求放在 header: token 里 */
    private String token;

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    /** 身份：USER / ADMIN */
    private String role;
}
