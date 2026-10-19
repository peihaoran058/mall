package com.example.mall.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回对象（笔记 4.3）。
 *
 * <p>为什么必须统一？<br>
 * 如果每个接口各返回各的（有的返回 User、有的返回 Map、有的返回 boolean），
 * 前端就得为每个接口写一套解析逻辑。统一成 {@code Result<T>} 后，
 * 前端一个拦截器就能处理全部响应，出错也能自动弹提示。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，见 {@link ResultCode} */
    private Integer code;

    /** 提示信息，前端可以直接弹给用户看 */
    private String msg;

    /** 业务数据 */
    private T data;

    /** 服务端时间戳，方便排查前后端时间不一致问题 */
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /* ==================== 成功 ==================== */

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), msg, data);
    }

    /* ==================== 失败 ==================== */

    public static <T> Result<T> fail() {
        return new Result<>(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMsg(), null);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(ResultCode.FAIL.getCode(), msg, null);
    }

    public static <T> Result<T> fail(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String msg) {
        return new Result<>(resultCode.getCode(), msg, null);
    }
}
