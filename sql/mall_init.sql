-- ============================================================================
-- SpringBoot 电商实战项目 —— 建表脚本
-- 数据库：mall    引擎：InnoDB    字符集：utf8mb4
-- 执行方式：mysql -uroot -p < mall_init.sql
-- ============================================================================

DROP DATABASE IF EXISTS mall;
CREATE DATABASE mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mall;

-- ----------------------------------------------------------------------------
-- 1. 前台用户表 sys_user
-- ----------------------------------------------------------------------------
-- ----------------------------------------------------------------------------
-- 1. 前台用户表 sys_user
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username     VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    password     VARCHAR(64)  NOT NULL                COMMENT '密码（MD5 加密存储）',
    nickname     VARCHAR(50)           DEFAULT NULL   COMMENT '昵称',
    phone        VARCHAR(20)           DEFAULT NULL   COMMENT '手机号',
    email        VARCHAR(100)          DEFAULT NULL   COMMENT '邮箱',
    avatar       VARCHAR(255)          DEFAULT NULL   COMMENT '头像地址',
    status       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态 1正常 0禁用',
    deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删 1已删',
    password_version INT NOT NULL DEFAULT 0 COMMENT '密码版本号，修改密码+1，用于token失效校验',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前台用户表';

-- ----------------------------------------------------------------------------
-- 2. 管理员表 admin
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS admin;
CREATE TABLE admin (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username     VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    password     VARCHAR(64)  NOT NULL                COMMENT '密码（MD5 加密存储）',
    nickname     VARCHAR(50)           DEFAULT NULL   COMMENT '昵称',
    role         VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' COMMENT '角色 ADMIN/SUPER_ADMIN',
    status       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态 1正常 0禁用',
    deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    password_version INT NOT NULL DEFAULT 0 COMMENT '密码版本号，修改密码+1，用于token失效校验',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';


-- ----------------------------------------------------------------------------
-- 3. 商品分类表 product_category（parent_id 自关联，构成树）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS product_category;
CREATE TABLE product_category (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id    BIGINT       NOT NULL DEFAULT 0      COMMENT '父分类id，0 表示一级分类',
    name         VARCHAR(50)  NOT NULL                COMMENT '分类名称',
    icon         VARCHAR(255)          DEFAULT NULL   COMMENT '分类图标',
    level        TINYINT      NOT NULL DEFAULT 1      COMMENT '层级 1一级 2二级',
    sort         INT          NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
    status       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态 1启用 0停用',
    deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ----------------------------------------------------------------------------
-- 4. 商品表 product
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS product;
CREATE TABLE product (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id    BIGINT        NOT NULL                COMMENT '分类id',
    name           VARCHAR(200)  NOT NULL                COMMENT '商品名称',
    subtitle       VARCHAR(255)           DEFAULT NULL   COMMENT '副标题/卖点',
    cover          VARCHAR(255)           DEFAULT NULL   COMMENT '主图',
    images         TEXT                                  COMMENT '轮播图，逗号分隔',
    detail         TEXT                                  COMMENT '商品详情',
    price          DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '售价',
    original_price DECIMAL(10,2)          DEFAULT NULL   COMMENT '原价（划线价）',
    stock          INT           NOT NULL DEFAULT 0      COMMENT '库存 【并发超卖实验核心字段】',
    sales          INT           NOT NULL DEFAULT 0      COMMENT '销量',
    status         TINYINT       NOT NULL DEFAULT 1      COMMENT '1上架 0下架',
    sort           INT           NOT NULL DEFAULT 0      COMMENT '排序值',
    deleted        TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ----------------------------------------------------------------------------
-- 5. 购物车表 shopping_cart
--    (user_id, product_id) 唯一索引：从数据库层面兜住"同一用户同一商品只有一条记录"
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS shopping_cart;
CREATE TABLE shopping_cart (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT   NOT NULL                COMMENT '用户id',
    product_id   BIGINT   NOT NULL                COMMENT '商品id',
    quantity     INT      NOT NULL DEFAULT 1      COMMENT '购买数量',
    selected     TINYINT  NOT NULL DEFAULT 1      COMMENT '是否勾选 1是 0否',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- ----------------------------------------------------------------------------
-- 6. 订单主表 orders
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no         VARCHAR(32)   NOT NULL                COMMENT '订单号（业务主键，对用户暴露）',
    user_id          BIGINT        NOT NULL                COMMENT '下单用户id',
    total_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '订单总金额',
    pay_amount       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '实付金额',
    pay_status       TINYINT       NOT NULL DEFAULT 0      COMMENT '0未支付 1已支付 2已退款',
    order_status     TINYINT       NOT NULL DEFAULT 0      COMMENT '0待付款 1待发货 2已发货 3已完成 4已取消',
    receiver_name    VARCHAR(50)            DEFAULT NULL   COMMENT '收货人',
    receiver_phone   VARCHAR(20)            DEFAULT NULL   COMMENT '收货电话',
    receiver_address VARCHAR(255)           DEFAULT NULL   COMMENT '收货地址',
    remark           VARCHAR(255)           DEFAULT NULL   COMMENT '订单备注',
    pay_time         DATETIME               DEFAULT NULL   COMMENT '支付时间',
    cancel_time      DATETIME               DEFAULT NULL   COMMENT '取消时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- ----------------------------------------------------------------------------
-- 7. 订单明细表 order_item（下单瞬间做商品快照，商品改价不影响历史订单）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS order_item;
CREATE TABLE order_item (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      BIGINT        NOT NULL                COMMENT '订单id',
    order_no      VARCHAR(32)   NOT NULL                COMMENT '订单号',
    product_id    BIGINT        NOT NULL                COMMENT '商品id',
    product_name  VARCHAR(200)  NOT NULL                COMMENT '商品名称快照',
    product_cover VARCHAR(255)           DEFAULT NULL   COMMENT '商品主图快照',
    price         DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '成交单价快照',
    quantity      INT           NOT NULL DEFAULT 1      COMMENT '购买数量',
    total_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '小计金额',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ----------------------------------------------------------------------------
-- 8. 订单日志表 order_log（第15课时 @Async 异步写入）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS order_log;
CREATE TABLE order_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(32)           DEFAULT NULL   COMMENT '订单号',
    content     VARCHAR(500)          DEFAULT NULL   COMMENT '日志内容',
    thread_name VARCHAR(100)          DEFAULT NULL   COMMENT '写入线程名（验证异步是否生效）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单日志表';

-- ============================================================================
-- 初始化数据
-- ============================================================================

-- 用户：账号 zhangsan / lisi，密码统一 123456
-- 密文 = MD5("123456" + "mall_salt") = de205270f073c16067ea4cc3118a781e
INSERT INTO sys_user (username, password, nickname, phone, email) VALUES
('zhangsan', 'de205270f073c16067ea4cc3118a781e', '张三', '13800000001', 'zhangsan@mall.com'),
('lisi',     'de205270f073c16067ea4cc3118a781e', '李四', '13800000002', 'lisi@mall.com');

-- 管理员：admin / 123456
INSERT INTO admin (username, password, nickname, role) VALUES
('admin', 'de205270f073c16067ea4cc3118a781e', '超级管理员', 'SUPER_ADMIN');

-- 分类：一级 + 二级
INSERT INTO product_category (id, parent_id, name, level, sort) VALUES
(1, 0, '手机数码', 1, 1),
(2, 0, '电脑办公', 1, 2),
(3, 0, '家用电器', 1, 3),
(11, 1, '手机',    2, 1),
(12, 1, '耳机',    2, 2),
(21, 2, '笔记本',  2, 1),
(22, 2, '显示器',  2, 2),
(31, 3, '冰箱',    2, 1);

-- 商品：把 id=1 的库存设为 10，专门给第13课时"20 线程压测超卖"用
INSERT INTO product (id, category_id, name, subtitle, cover, detail, price, original_price, stock, sales, status, sort) VALUES
(1, 11, '骁龙旗舰手机 X100', '12GB+256GB 超清影像', '/upload/demo/phone1.jpg', '旗舰芯片，2 亿像素主摄', 3999.00, 4599.00, 10,  0, 1, 1),
(2, 11, '轻薄手机 Air 5',    '8GB+128GB 长续航',    '/upload/demo/phone2.jpg', '175g 轻薄机身',        1999.00, 2299.00, 100, 20, 1, 2),
(3, 12, '降噪蓝牙耳机 Pro',   '主动降噪 40dB',       '/upload/demo/ear1.jpg',   '双设备连接',           599.00,  799.00,  200, 55, 1, 3),
(4, 21, '轻薄笔记本 14 Pro',  'i7 16G 1T',           '/upload/demo/laptop1.jpg','2.8K 120Hz 高刷屏',    6999.00, 7999.00, 50,  8,  1, 4),
(5, 22, '27 英寸 2K 显示器',  '165Hz 电竞屏',         '/upload/demo/screen1.jpg','广色域低蓝光',         1299.00, 1599.00, 80,  12, 1, 5),
(6, 31, '双开门节能冰箱',      '452L 大容量',         '/upload/demo/fridge1.jpg','风冷无霜',             3299.00, 3899.00, 30,  4,  1, 6);

-- 为购物车/订单实验准备一个测试用户（id=1 张三）
INSERT INTO shopping_cart (user_id, product_id, quantity, selected) VALUES
(1, 2, 1, 1),
(1, 3, 2, 1);

-- ============================================================================
-- 课堂上验证超卖用的重置语句（第13课时反复使用）
-- UPDATE product SET stock = 10, sales = 0 WHERE id = 1;
-- DELETE FROM orders WHERE user_id = 1;
-- DELETE FROM order_item WHERE order_no LIKE 'MALL%';
-- ============================================================================
