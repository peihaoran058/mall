/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.example.mall.demo.TransactionDemoService
 *  com.example.mall.entity.OrderLog
 *  com.example.mall.mapper.OrderLogMapper
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 */
package com.example.mall.demo;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mall.entity.OrderLog;
import com.example.mall.mapper.OrderLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionDemoService {
    private static final Logger log = LoggerFactory.getLogger(TransactionDemoService.class);
    private final OrderLogMapper orderLogMapper;

    @Transactional(rollbackFor={Exception.class})
    public void rollbackSuccess(String tag) {
        this.insertLog(tag + "-ROLLBACK-OK", "\u7b2c\u4e00\u6761\uff1a\u5373\u5c06\u629b\u5f02\u5e38\uff0c\u5e94\u5f53\u56de\u6eda");
        log.info("\u3010\u5b9e\u9a8c\u4e00\u3011\u5df2\u63d2\u5165\u8bb0\u5f55\uff0c\u63a5\u4e0b\u6765 int i = 1 / 0");
        int i = 1 / 0;
        log.info("\u6c38\u8fdc\u4e0d\u4f1a\u6267\u884c\u7684\u4ee3\u7801\uff1a{}", (Object)i);
    }

    @Transactional
    public void rollbackFailNoRollbackFor(String tag) throws Exception {
        this.insertLog(tag + "-NOROLLBACK", "\u7b2c\u4e00\u6761\uff1a\u5373\u5c06\u629b\u53d7\u68c0\u5f02\u5e38\uff0c\u672c\u5e94\u56de\u6eda");
        log.info("\u3010\u5b9e\u9a8c\u4e8c\u3011\u5df2\u63d2\u5165\u8bb0\u5f55\uff0c\u63a5\u4e0b\u6765\u629b\u53d7\u68c0\u5f02\u5e38\uff08\u53d7\u68c0\u5f02\u5e38\u9ed8\u8ba4\u4e0d\u56de\u6eda\uff09");
        throw new Exception("\u8fd9\u662f\u4e00\u4e2a\u53d7\u68c0\u5f02\u5e38\uff0c@Transactional \u9ed8\u8ba4\u4e0d\u4f1a\u56de\u6eda");
    }

    @Transactional(rollbackFor={Exception.class})
    public void rollbackFailCatch(String tag) {
        this.insertLog(tag + "-CATCH", "\u7b2c\u4e00\u6761\uff1a\u5f02\u5e38\u88ab try-catch \u5403\u6389\uff0c\u672c\u5e94\u56de\u6eda");
        try {
            int n = 1 / 0;
        }
        catch (Exception e) {
            log.info("\u3010\u5b9e\u9a8c\u4e09\u3011\u5f02\u5e38\u88ab catch \u5403\u6389\u4e86\uff0c\u4e8b\u52a1\u4e0d\u4f1a\u56de\u6eda | msg={}", (Object)e.getMessage());
        }
        log.info("\u3010\u5b9e\u9a8c\u4e09\u3011\u65b9\u6cd5\u6b63\u5e38\u8fd4\u56de\uff0c\u4e8b\u52a1\u5373\u5c06\u63d0\u4ea4");
    }

    public void rollbackFailSelfInvocation(String tag) {
        log.info("\u3010\u5b9e\u9a8c\u56db\u3011\u5916\u5c42\u65b9\u6cd5\u6ca1\u6709\u4e8b\u52a1\uff0c\u5373\u5c06 this \u8c03\u7528\u5185\u90e8\u5e26\u4e8b\u52a1\u7684\u65b9\u6cd5");
        this.innerTransactional(tag);
    }

    @Transactional(rollbackFor={Exception.class})
    public void innerTransactional(String tag) {
        this.insertLog(tag + "-SELF", "\u7b2c\u4e00\u6761\uff1a\u81ea\u8c03\u7528\u5bfc\u81f4\u4e8b\u52a1\u5931\u6548\uff0c\u672c\u5e94\u56de\u6eda");
        log.info("\u3010\u5b9e\u9a8c\u56db\u3011\u5185\u5c42\u65b9\u6cd5\u6267\u884c\uff08\u4e8b\u52a1\u672a\u751f\u6548\uff09\uff0c\u5373\u5c06\u629b\u5f02\u5e38");
        throw new RuntimeException("\u81ea\u8c03\u7528\u573a\u666f\uff0c@Transactional \u5df2\u88ab\u7ed5\u8fc7");
    }

    @Transactional(rollbackFor={Exception.class})
    private void privateTransactional(String tag) {
        this.insertLog(tag + "-PRIVATE", "\u7b2c\u4e00\u6761\uff1aprivate \u65b9\u6cd5\u4e0a\u7684\u4e8b\u52a1\u6ce8\u89e3\u65e0\u6548");
        throw new RuntimeException("private \u65b9\u6cd5\u4e0a\u7684 @Transactional \u4e0d\u751f\u6548");
    }

    public long countByTag(String tag) {
        Long count = this.orderLogMapper.selectCount(Wrappers.<OrderLog>lambdaQuery().likeRight(OrderLog::getContent, tag + "-"));
        return count == null ? 0L : count;
    }

    public int cleanDemoData() {
        return this.orderLogMapper.delete(Wrappers.<OrderLog>lambdaQuery().likeRight(OrderLog::getContent, "TX-"));
    }

    private void insertLog(String content, String desc) {
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderNo("TX-DEMO");
        orderLog.setContent(content + " | " + desc);
        orderLog.setThreadName(Thread.currentThread().getName());
        this.orderLogMapper.insert(orderLog);
        log.info("\u3010\u63d2\u5165\u8bb0\u5f55\u3011content={}", (Object)content);
    }

    public TransactionDemoService(OrderLogMapper orderLogMapper) {
        this.orderLogMapper = orderLogMapper;
    }
}

