package com.example.mall.order.oversell;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * 超卖实验的数据服务：查库存 / 重置库存 / 清理压测订单
 * ============================================================================
 *
 * <p>压测前必须把现场恢复干净（库存归位、历史订单清掉），
 * 否则上一轮的结果会污染下一轮的对比 —— 重置库存和清理订单
 * 就是从这里提供给演示台和压测入口共用的。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OversellDataService {

    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;

    /**
     * 查商品库存概况（前端库存看板直接渲染这个结构）。
     * <p>oversell = 库存是否已经变成负数，也就是"超卖"的判定标准。</p>
     */
    public Map<String, Object> stockInfo(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在：id=" + productId);
        }
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("productId", product.getId());
        data.put("productName", product.getName());
        data.put("stock", product.getStock());
        data.put("sales", product.getSales());
        data.put("oversell", product.getStock() != null && product.getStock() < 0);
        return data;
    }

    /**
     * 重置现场：库存归位、销量清零。演示每一轮压测前都要跑一次。
     */
    public Map<String, Object> resetStock(Long productId, Integer stock) {
        if (stock == null || stock < 0) {
            throw new BusinessException("初始库存必须是大于等于 0 的整数");
        }
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .set(Product::getStock, stock)
                .set(Product::getSales, 0)
                .eq(Product::getId, productId));
        if (rows == 0) {
            throw new BusinessException("商品不存在：id=" + productId);
        }
        log.info("【课堂实验】重置库存 | productId={} | stock={}", productId, stock);
        return stockInfo(productId);
    }

    /**
     * 清理压测产生的订单（只认 remark = loadtest 的订单，不碰真实订单）。
     * <p>返回结构 {orderRows, itemRows} 和前端提示文案的字段对齐。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cleanOrders() {
        List<Orders> loadtestOrders = ordersMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getRemark, OversellSupport.LOADTEST_REMARK));

        Map<String, Object> data = new LinkedHashMap<>(4);
        if (loadtestOrders.isEmpty()) {
            data.put("orderRows", 0);
            data.put("itemRows", 0);
            return data;
        }

        List<Long> orderIds = loadtestOrders.stream().map(Orders::getId).collect(Collectors.toList());
        int itemRows = orderItemMapper.delete(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getOrderId, orderIds));
        int orderRows = ordersMapper.delete(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getRemark, OversellSupport.LOADTEST_REMARK));

        log.info("【课堂实验】清理压测订单 | 订单 {} 笔 | 明细 {} 条", orderRows, itemRows);
        data.put("orderRows", orderRows);
        data.put("itemRows", itemRows);
        return data;
    }
}
