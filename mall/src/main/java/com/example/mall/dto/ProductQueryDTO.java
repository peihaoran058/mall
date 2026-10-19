package com.example.mall.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品查询条件。
 *
 * <p><b>课上重点：分页参数一定要有上限保护。</b><br>
 * 前端传 {@code pageSize=100000} 就是一次全表扫描 + 大对象序列化，
 * 服务端内存瞬间打满。除了 DTO 上限制，MyBatis-Plus 分页插件也配了
 * {@code setMaxLimit(500)} 做双保险。</p>
 */
@Data
public class ProductQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品名称，模糊匹配 */
    private String name;

    private Long categoryId;

    /** 1 上架 0 下架，null 表示不限 */
    private Integer status;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /**
     * 排序字段。
     * <p><b>注意：这个值绝对不能直接拼进 SQL 的 ORDER BY！</b>
     * {@code orderBy} 后面跟的是"字段名"，不是"值"，没法用 {@code ?} 占位符，
     * 所以它是唯一一个"必须靠白名单"来防注入的位置。
     * 前端传 {@code price; drop table product --} 这种值，只有白名单能挡住。
     * 具体实现在 {@code ProductServiceImpl#userPage}。</p>
     * <p>可选值：price / sales / id（其他值一律走默认排序）</p>
     */
    private String sortBy;

    /** 排序方向：asc / desc，默认 desc */
    private String orderBy;

    private Long pageNum = 1L;

    private Long pageSize = 10L;
}
