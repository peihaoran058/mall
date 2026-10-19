package com.example.mall.exception;

import com.example.mall.common.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异常。
 *
 * <p>为什么不用 RuntimeException 直接抛？<br>
 * 因为业务失败需要携带"错误码 + 可展示给用户的文案"，
 * 而 {@code RuntimeException} 只有 message。有了错误码，前端才能做差异化处理
 * （比如 1001 用户名已存在 → 表单标红；401 → 跳登录页）。</p>
 *
 * <p><b>课上重点：</b>继承 RuntimeException 还是 Exception？<br>
 * 必须继承 RuntimeException（非受检异常）。如果继承 Exception，
 * Spring 事务默认只在遇到 RuntimeException 时回滚，
 * 抛这种异常事务不会回滚——这就是笔记 5.4 "事务失效场景"里的一条。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final Integer code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
    }

    public BusinessException(String msg) {
        super(msg);
        this.code = ResultCode.FAIL.getCode();
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }

    /** 业务异常不需要堆栈，省一次 fillInStackTrace 的开销 */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
