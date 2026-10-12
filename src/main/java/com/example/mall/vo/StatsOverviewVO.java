package com.example.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台仪表盘统计概览。
 *
 * <p><b>课上重点：为什么统计接口要单独设计，而不是让前端"多调几个列表接口自己算"？</b></p>
 * <ol>
 *     <li><b>性能</b>：前端算"订单状态分布"要先把全部订单拉到浏览器，
 *         订单表 100 万行时直接卡死。数据库用 {@code GROUP BY} 一条 SQL 就出结果。</li>
 *     <li><b>口径统一</b>："销售额"到底是下单金额还是已支付金额？
 *         放在服务端只有一处定义，放在前端每个页面都可能算得不一样。</li>
 *     <li><b>安全</b>：前端不该拿到全量明细数据。</li>
 * </ol>
 *
 * <p>图表数据统一用 {@link ChartItem}（name + value），
 * 因为 ECharts 的饼图、柱状图、折线图都吃这个结构，前端不用为每张图写转换逻辑。</p>
 */
@Data
public class StatsOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /* ---------- 顶部 4 个数字卡片 ---------- */

    /** 用户总数 */
    private Long userCount;

    /** 商品总数（未删除） */
    private Long productCount;

    /** 上架商品数 */
    private Long onSaleProductCount;

    /** 订单总数 */
    private Long orderCount;

    /** 今日新增订单数 */
    private Long todayOrderCount;

    /** 已支付订单的成交金额合计（口径：pay_status = 1） */
    private BigDecimal salesAmount;

    /** 分类总数 */
    private Long categoryCount;

    /** 库存预警数量：库存 < 10 且已上架的商品数 */
    private Long lowStockCount;

    /* ---------- 图表数据 ---------- */

    /** 订单状态分布（饼图） */
    private List<ChartItem> orderStatusDist = new ArrayList<>();

    /** 商品销量 TOP5（横向柱状图） */
    private List<ChartItem> topProducts = new ArrayList<>();

    /** 近 7 日订单数趋势（折线图） */
    private List<ChartItem> orderTrend = new ArrayList<>();

    /**
     * 图表通用数据项。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 维度名称，如 "已发货" / "2026-09-20" / 商品名 */
        private String name;

        /** 指标值 */
        private Long value;
    }
}
