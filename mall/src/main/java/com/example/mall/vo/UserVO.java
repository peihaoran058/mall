package com.example.mall.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台用户列表项。
 *
 * <p><b>课上重点：为什么不能直接返回 {@code SysUser} 实体？</b></p>
 * <p>{@code SysUser} 里有 {@code deleted} 这类内部字段，也有密码字段
 * （虽然加了 {@code @JsonIgnore} 兜底）。一旦以后有人不小心把注解删了、
 * 或者加了 {@code @JsonView}，密码就泄露了。</p>
 * <p>后台接口<br>返回 VO、只暴露需要的字段，是最省心的做法 ——
 * <b>"不返回"比"记得加 @JsonIgnore"更可靠。</b></p>
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    private String email;

    private String avatar;

    /** 1 正常 0 禁用 */
    private Integer status;

    /** 该用户的订单总数（统计字段） */
    private Long orderCount;

    private LocalDateTime createTime;
}
