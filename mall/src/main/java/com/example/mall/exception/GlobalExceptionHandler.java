package com.example.mall.exception;

import com.example.mall.common.Result;
import com.example.mall.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（笔记 4.5）。
 *
 * <p>没有它，Service 里抛出的异常会变成 Tomcat 的白页 / 一堆堆栈信息；
 * 有了它，所有异常都会变成统一的 {@code Result} 结构，前端处理逻辑只有一套。</p>
 *
 * <p><b>课上重点：</b>@RestControllerAdvice 能拦到什么？<br>
 * 只能拦到"进入 Controller 之后"抛出的异常。像 404（找不到接口）、
 * Filter 里抛的异常是拦不到的，那些要走 ErrorController / Filter 自己处理。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：可预期，只记 warn，不打印堆栈。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 | uri={} | code={} | msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * @Valid 校验失败（RequestBody JSON 参数）。
     * 只返回第一个错误信息，前端提示更友好。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败 | {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 表单/Query 参数绑定失败。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数绑定失败 | {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 必填请求参数缺失。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 请求体不是合法 JSON。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败 | {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误，请检查 JSON 是否合法");
    }

    /**
     * 请求方式不对，比如接口是 POST 却用 GET 访问。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED.getCode(),
                "请求方式不支持，请使用 " + e.getSupportedHttpMethods());
    }

    /**
     * 兜底：上面都没匹配到的异常。
     * 这里必须打印完整堆栈，否则线上出错根本查不出来。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 | uri={}", request.getRequestURI(), e);
        return Result.fail(ResultCode.FAIL.getCode(), "系统繁忙，请稍后重试");
    }
}
