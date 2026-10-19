package com.example.mall.common;

/**
 * 项目常量。
 *
 * <p>把 Redis Key 前缀、请求头名字集中在这里，避免各处硬编码字符串——
 * 打错一个字符的 Key 不会报错，只会"缓存永远不命中"，这种 BUG 最难查。</p>
 */
public interface Constants {

    /* ==================== 请求头 / Session ==================== */

    /** 前端把登录令牌放在这个请求头里 */
    String HEADER_TOKEN = "token";

    /** Session 中保存登录用户的属性名（第4课时 session 方案） */
    String SESSION_LOGIN_USER = "LOGIN_USER";

    /** Session 中保存登录管理员的属性名 */
    String SESSION_LOGIN_ADMIN = "LOGIN_ADMIN";

    /** 拦截器解析出的用户 id，放进 request attribute 供 Controller 取用 */
    String REQ_USER_ID = "REQ_USER_ID";

    /** 拦截器解析出的管理员 id */
    String REQ_ADMIN_ID = "REQ_ADMIN_ID";

    /** 当前请求身份类型：USER / ADMIN */
    String REQ_ROLE = "REQ_ROLE";

    String ROLE_USER = "USER";
    String ROLE_ADMIN = "ADMIN";

    /* ==================== Redis Key 前缀 ==================== */

    /** 前台用户登录令牌：mall:login:user:{token} -> SysUser */
    String REDIS_LOGIN_USER = "mall:login:user:";

    /** 后台管理员登录令牌：mall:login:admin:{token} -> Admin */
    String REDIS_LOGIN_ADMIN = "mall:login:admin:";

    /** 登录令牌有效期（秒），30 分钟 */
    long LOGIN_TOKEN_TTL = 30 * 60L;

    /** 商品详情缓存：@Cacheable 的 value，最终 key = mall:cache:product::detail::{id} */
    String CACHE_PRODUCT_DETAIL = "mall:cache:product";

    /** 缓存穿透演示：空对象占位，短 TTL 防止长期脏数据 */
    String REDIS_NULL_HOLDER = "mall:cache:product:null:";

    /** 缓存击穿演示：重建缓存的互斥锁 */
    String REDIS_LOCK_CACHE_REBUILD = "mall:lock:cache:rebuild:";

    /** 下单分布式锁前缀：mall:lock:order:{productId} 或 用户维度 */
    String REDIS_LOCK_ORDER = "mall:lock:order:";

    /** 订单防重复提交锁 */
    String REDIS_LOCK_SUBMIT = "mall:lock:submit:";

    /* ==================== 业务常量 ==================== */

    /** 订单号前缀 */
    String ORDER_NO_PREFIX = "MALL";

    /** 密码加密盐值 */
    String PASSWORD_SALT = "mall_salt";

    /** 允许上传的图片后缀 */
    String[] ALLOWED_IMAGE_EXT = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"};

    /** 商品状态：上架 */
    Integer PRODUCT_ON = 1;
    /** 商品状态：下架 */
    Integer PRODUCT_OFF = 0;

    /** 支付状态：未支付 / 已支付 / 已退款 */
    Integer PAY_STATUS_UNPAID = 0;
    Integer PAY_STATUS_PAID = 1;
    Integer PAY_STATUS_REFUND = 2;

    /** 订单状态：待付款 / 待发货 / 已发货 / 已完成 / 已取消 */
    Integer ORDER_STATUS_UNPAID = 0;
    Integer ORDER_STATUS_PAID = 1;
    Integer ORDER_STATUS_SENT = 2;
    Integer ORDER_STATUS_FINISHED = 3;
    Integer ORDER_STATUS_CANCELED = 4;

    /** 商城分类 */
    String REDIS_CATEGORY_TREE = "redis:mall:tree";

    String REDIS_PRODUCT_DETAIL = "redis:product:detail";

}
