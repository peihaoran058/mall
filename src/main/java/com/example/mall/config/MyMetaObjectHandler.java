package com.example.mall.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充 createTime / updateTime。
 *
 * <p>实体里字段加了 {@code @TableField(fill = FieldFill.INSERT)} 才会生效。
 * 注意：MyBatis-Plus 的自动填充走的是 insert/update 语句的动态拼接，
 * 如果你自己写了 XML 的 insert，它**不会**生效——这是新手最常见的困惑。</p>
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
