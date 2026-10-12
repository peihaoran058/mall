package com.example.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员（对应表 admin）。
 *
 * <p><b>课上重点：为什么用户和管理员要分两张表？</b><br>
 * 一张表用 {@code type} 字段区分也不是不行，但两边的字段差异很大
 * （用户有收货信息、会员等级；管理员有角色、权限、操作日志），
 * 强行合表会出现大量空字段，而且权限校验容易漏——一个 SQL 注入就能让
 * 普通用户拿到管理员 token。分表 + 登录时写入不同的 Redis key，是最简单的隔离方式。</p>
 */
@Data
@TableName("admin")
public class Admin implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String nickname;

    /** 角色：ADMIN / SUPER_ADMIN */
    private String role;

    private Integer PasswordVersion;

    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
