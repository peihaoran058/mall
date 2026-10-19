package com.example.mall.common;

import lombok.Getter;

/**
 * 全局响应状态码。
 *
 * <p>为什么要自己定义一套码？<br>
 * HTTP 状态码只有 200/400/500，表达不了"用户名已存在"这种业务语义。
 * 前后端约定：HTTP 永远返回 200，真正的结果看 body 里的 code。</p>
 *
 * <p>码段规划（团队协作时按模块分段，避免互相冲突）：</p>
 * <ul>
 *     <li>2xx / 4xx / 5xx：通用</li>
 *     <li>10xx：用户模块</li>
 *     <li>11xx：管理员模块</li>
 *     <li>20xx：商品与分类</li>
 *     <li>30xx：购物车</li>
 *     <li>40xx：订单</li>
 *     <li>50xx：文件与公共</li>
 *     <li>60xx：并发/分布式锁</li>
 * </ul>
 */
@Getter
public enum ResultCode {

    /* ---------- 通用 ---------- */
    SUCCESS(200, "操作成功"),
    FAIL(500, "系统繁忙，请稍后重试"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),

    /* ---------- 用户模块 10xx ---------- */
    USERNAME_EXISTS(1001, "用户名已存在"),
    USER_NOT_EXISTS(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "账号或密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),
    PASSWORD_NOT_MATCH(1005, "两次输入的密码不一致"),
    PASSWORD_NOT_CHANGED(1006,"新密码不能和原密码相同"),

    /* ---------- 管理员模块 11xx ---------- */
    ADMIN_LOGIN_ERROR(1101, "管理员账号或密码错误"),

    /* ---------- 商品与分类 20xx ---------- */
    PRODUCT_NOT_EXISTS(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架"),
    CATEGORY_NOT_EXISTS(2003, "分类不存在"),
    CATEGORY_HAS_CHILDREN(2004, "该分类下还有子分类，无法删除"),
    CATEGORY_HAS_PRODUCT(2005, "该分类下还有商品，无法删除"),

    /* ---------- 购物车 30xx ---------- */
    CART_ITEM_NOT_EXISTS(3001, "购物车中没有该商品"),
    CART_EMPTY(3002, "请先勾选要结算的商品"),
    STOCK_NOT_ENOUGH(3003, "商品库存不足"),

    /* ---------- 订单 40xx ---------- */
    ORDER_NOT_EXISTS(4001, "订单不存在"),
    ORDER_STATUS_ERROR(4002, "当前订单状态不允许该操作"),
    ORDER_NO_PERMISSION(4003, "无权操作他人的订单"),
    ORDER_REPEAT_SUBMIT(4004, "请勿重复提交订单"),
    ORDER_LOG_ERROR(4005, "订单日志创建失败"),

    /* ---------- 文件与公共 50xx ---------- */
    FILE_UPLOAD_ERROR(5001, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(5002, "不支持的文件类型"),
    FILE_EMPTY(5003, "上传文件不能为空"),

    /* ---------- 并发 60xx ---------- */
    LOCK_ACQUIRE_FAIL(6001, "当前下单人数过多，请稍后重试");

    private final Integer code;
    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
