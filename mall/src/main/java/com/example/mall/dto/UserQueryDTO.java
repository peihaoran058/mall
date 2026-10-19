package com.example.mall.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 后台用户列表查询条件。
 */
@Data
public class UserQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 账号 / 昵称 / 手机号，模糊匹配 */
    private String keyword;

    /** 1 正常 0 禁用，null 表示不限 */
    private Integer status;

    private Long pageNum = 1L;

    private Long pageSize = 10L;
}
