package com.example.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 前台用户（对应表 sys_user）。
 *
 * <p><b>课上重点：{@code @TableLogic} 到底做了什么？</b><br>
 * 加了这个注解后，MyBatis-Plus 自动改写 SQL：</p>
 * <ul>
 *     <li>{@code selectById(1)} → {@code select ... where id=1 and deleted=0}</li>
 *     <li>{@code deleteById(1)} → {@code update sys_user set deleted=1 where id=1}</li>
 *     <li>所有 list / page 查询也会自动带上 {@code deleted=0}</li>
 * </ul>
 *
 * <p><b>坑：</b>自己写的 XML SQL 不带这个条件，需要手写 {@code and deleted = 0}；
 * 唯一索引 {@code uk_username} 也要配合处理，否则删掉后同名用户无法再注册
 * （常见解法：删除时把 username 改成 {@code 旧名_deleted_时间戳}）。</p>
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /**
     * 密码密文。
     * <p>{@code @JsonIgnore} 是关键：不然查询用户接口会把密码一起返回给前端，
     * 这是非常典型的安全事故。</p>
     */
    @JsonIgnore
    private String password;

    private String nickname;

    private String phone;

    private String email;

    private String avatar;

    private Integer PasswordVersion;

    /** 1 正常 0 禁用 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
