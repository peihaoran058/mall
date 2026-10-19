package com.example.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单操作日志（对应表 order_log），第15课时 {@code @Async} 异步写入的落库对象。
 *
 * <p>{@code threadName} 字段是专门为教学加的：异步生效时，
 * 这里记录的线程名会是 {@code mall-async-x}；异步失效时是 {@code http-nio-8080-exec-x}。
 * 课堂上直接 select 一下就能用数据证明"异步到底有没有生效"。</p>
 */
@Data
@TableName("order_log")
public class OrderLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String content;

    /** 写入线程名 —— 验证 @Async 是否真的生效 */
    private String threadName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
