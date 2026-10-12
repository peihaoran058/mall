package com.example.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mall.common.Constants;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductCategoryMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.mapper.SysUserMapper;
import com.example.mall.service.StatsService;
import com.example.mall.vo.StatsOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 后台数据看板实现。
 *
 * <p><b>课上重点一：统计一定要交给数据库做。</b><br>
 * 下面每个指标都是一条 SQL 直接出结果，没有任何"查出全部再在内存里循环"的写法。
 * 对比一下两种写法的代价（假设 10 万订单）：</p>
 * <ul>
 *     <li>数据库聚合：{@code SELECT COUNT(*) ... GROUP BY status} —— 走索引，几十毫秒，网络只传 5 行</li>
 *     <li>内存聚合：查出 10 万行（几十 MB），序列化传输，再循环统计 —— 秒级，还可能 OOM</li>
 * </ul>
 *
 * <p><b>课上重点二：{@code QueryWrapper.select(...)} 可以直接写 SQL 片段和聚合函数。</b><br>
 * 但要注意参数必须是<b>代码里写死的字符串</b>，不能把前端传的值拼进去，
 * 否则就是 SQL 注入。需要动态值一律用 {@code .eq()/.ge()} 这类带占位符的方法。</p>
 *
 * <p><b>课上重点三：接口拆不拆？</b><br>
 * 这里把看板所有指标塞进<b>一个</b>接口返回。理由：它们总是在同一个页面同时被用到，
 * 拆成 8 个接口意味着首屏要发 8 次请求（浏览器同域并发上限 6，还会排队）。
 * 判断标准是"<b>是否总是一起用</b>"，而不是"一个接口对应一张表"。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    /** 库存预警阈值 */
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final SysUserMapper userMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final OrdersMapper ordersMapper;

    @Override
    public StatsOverviewVO overview() {
        StatsOverviewVO vo = new StatsOverviewVO();

        // ---------- 1. 数字卡片 ----------
        vo.setUserCount(userMapper.selectCount(null));
        vo.setCategoryCount(categoryMapper.selectCount(null));
        vo.setProductCount(productMapper.selectCount(null));
        vo.setOnSaleProductCount(productMapper.selectCount(
                new QueryWrapper<Product>().eq("status", Constants.PRODUCT_ON)));
        vo.setLowStockCount(productMapper.selectCount(new QueryWrapper<Product>()
                .eq("status", Constants.PRODUCT_ON)
                .lt("stock", LOW_STOCK_THRESHOLD)));

        vo.setOrderCount(ordersMapper.selectCount(null));
        vo.setTodayOrderCount(ordersMapper.selectCount(new QueryWrapper<Orders>()
                .ge("create_time", LocalDate.now().atStartOfDay())));

        // 成交金额：只统计已支付订单，避免把"拍下未付"的钱也算成营收
        vo.setSalesAmount(sumPaidAmount());

        // ---------- 2. 图表数据 ----------
        vo.setOrderStatusDist(orderStatusDist());
        vo.setTopProducts(topProducts());
        vo.setOrderTrend(orderTrend());

        return vo;
    }

    /** 已支付订单的实付金额合计 */
    private BigDecimal sumPaidAmount() {
        List<Map<String, Object>> rows = ordersMapper.selectMaps(new QueryWrapper<Orders>()
                .select("IFNULL(SUM(pay_amount), 0) AS total")
                .eq("pay_status", Constants.PAY_STATUS_PAID));
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Object total = rows.get(0).get("total");
        if (total == null) {
            return BigDecimal.ZERO;
        }
        return total instanceof BigDecimal ? (BigDecimal) total : new BigDecimal(total.toString());
    }

    /**
     * 订单状态分布。
     * <p>注意：{@code GROUP BY} 只会返回"存在数据的状态"，
     * 所以要把 5 个状态先铺满，缺的补 0 —— 否则前端饼图会少几块、
     * 图例和实际状态对不上，运营看了会以为数据丢了。</p>
     */
    private List<StatsOverviewVO.ChartItem> orderStatusDist() {
        List<Map<String, Object>> rows = ordersMapper.selectMaps(new QueryWrapper<Orders>()
                .select("order_status AS st", "COUNT(*) AS cnt")
                .groupBy("order_status"));

        Map<Integer, Long> countMap = new HashMap<>(8);
        for (Map<String, Object> row : rows) {
            Object st = row.get("st");
            Object cnt = row.get("cnt");
            if (st != null && cnt != null) {
                countMap.put(((Number) st).intValue(), ((Number) cnt).longValue());
            }
        }

        // 用 LinkedHashMap 保证顺序稳定（待付款 → 待发货 → 已发货 → 已完成 → 已取消）
        Map<Integer, String> names = new LinkedHashMap<>(8);
        names.put(Constants.ORDER_STATUS_UNPAID, "待付款");
        names.put(Constants.ORDER_STATUS_PAID, "待发货");
        names.put(Constants.ORDER_STATUS_SENT, "已发货");
        names.put(Constants.ORDER_STATUS_FINISHED, "已完成");
        names.put(Constants.ORDER_STATUS_CANCELED, "已取消");

        List<StatsOverviewVO.ChartItem> list = new ArrayList<>(names.size());
        names.forEach((status, name) ->
                list.add(new StatsOverviewVO.ChartItem(name, countMap.getOrDefault(status, 0L))));
        return list;
    }

    /** 销量 TOP5 */
    private List<StatsOverviewVO.ChartItem> topProducts() {
        List<Product> products = productMapper.selectList(new QueryWrapper<Product>()
                .select("name", "sales")
                .orderByDesc("sales")
                .last("LIMIT 5"));
        List<StatsOverviewVO.ChartItem> list = new ArrayList<>(products.size());
        for (Product p : products) {
            list.add(new StatsOverviewVO.ChartItem(p.getName(),
                    p.getSales() == null ? 0L : p.getSales().longValue()));
        }
        return list;
    }

    /**
     * 近 7 日订单趋势。
     * <p>同样要补零：某天没有订单时数据库不会返回这一行，
     * 折线图就会"跳过去"，看起来像那天不存在。补零之后曲线才是连续的。</p>
     */
    private List<StatsOverviewVO.ChartItem> orderTrend() {
        LocalDate from = LocalDate.now().minusDays(6);
        List<Map<String, Object>> rows = ordersMapper.selectMaps(new QueryWrapper<Orders>()
                .select("DATE(create_time) AS d", "COUNT(*) AS cnt")
                .ge("create_time", from.atStartOfDay())
                .groupBy("DATE(create_time)"));

        Map<String, Long> countMap = new HashMap<>(16);
        for (Map<String, Object> row : rows) {
            Object d = row.get("d");
            Object cnt = row.get("cnt");
            if (d != null && cnt != null) {
                // JDBC 返回的可能是 java.sql.Date / LocalDate / String，统一取 yyyy-MM-dd 前 10 位
                countMap.put(String.valueOf(d).substring(0, 10), ((Number) cnt).longValue());
            }
        }

        List<StatsOverviewVO.ChartItem> list = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate day = from.plusDays(i);
            String key = day.toString();
            list.add(new StatsOverviewVO.ChartItem(
                    day.format(DAY_FMT), countMap.getOrDefault(key, 0L)));
        }
        return list;
    }
}
