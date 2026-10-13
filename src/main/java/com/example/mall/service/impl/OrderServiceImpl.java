package com.example.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mall.common.Constants;
import com.example.mall.common.PageResult;
import com.example.mall.common.ResultCode;
import com.example.mall.dto.OrderQueryDTO;
import com.example.mall.dto.OrderSubmitDTO;
import com.example.mall.entity.OrderItem;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.entity.ShoppingCart;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.OrderItemMapper;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.mapper.ShoppingCartMapper;
import com.example.mall.service.OrderService;
import com.example.mall.util.OrderNoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ShoppingCartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Orders submit(Long userId, OrderSubmitDTO dto) {
        // 1.查询该用户的购物车信息
        List<ShoppingCart> carts = cartMapper.selectList(new LambdaQueryWrapper<ShoppingCart>().eq(ShoppingCart::getUserId, userId).eq(ShoppingCart::getSelected, 1));
        Map<Long, Integer> productIdQuantityMap = carts.stream()
                .collect(Collectors.toMap(
                        ShoppingCart::getProductId,
                        ShoppingCart::getQuantity,
                        Integer::sum   // 同商品quantity累加
                ));
        List<Long> cartsIdS = carts.stream().map(ShoppingCart::getId).collect(Collectors.toList());


        Long orderId = OrderNoUtil.snowflakeLike();
        String orderNo = OrderNoUtil.timeRandom();
        // 2.查询要购买商品的库存
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>().in(Product::getId, productIdQuantityMap.keySet()));
        if (!(products.size() >0)){
            log.error("无商品");
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXISTS);
        }
        products = products.stream().sorted(Comparator.comparing(Product::getId)).collect(Collectors.toList());
        BigDecimal orderTotalAmount = new BigDecimal(0);
        for (Product product : products) {

            if (product.getStatus() != 1) {
                log.error("商品 {} 已下架", product.getName());
                throw new BusinessException(ResultCode.PRODUCT_OFF_SHELF);
            }

            // 3.判断库存是否充足
            if (product.getStock() < productIdQuantityMap.get(product.getId())) {
                log.error("商品 {} 库存不足", product.getName());
                throw new RuntimeException("商品 " + product.getName() + " 库存不足");
            }

            // 4.扣减库存
            productMapper.deductStock(product.getId(), productIdQuantityMap.get(product.getId()));

            // 5.创建订单详情
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(orderId);
            orderItem.setOrderNo(orderNo);
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setProductCover(product.getCover());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(productIdQuantityMap.get(product.getId()));
            BigDecimal multiply = product.getPrice().multiply(new BigDecimal(productIdQuantityMap.get(product.getId())));
            orderTotalAmount = orderTotalAmount.add(multiply);
            orderItem.setTotalAmount(multiply);
            orderItemMapper.insert(orderItem);
        }
        // 6.创建订单
        Orders orders = new Orders();
        orders.setId(orderId);
        orders.setOrderNo(orderNo);
        orders.setUserId(userId);
        orders.setTotalAmount(orderTotalAmount);
        orders.setPayAmount(orderTotalAmount);
        orders.setPayStatus(Constants.PAY_STATUS_UNPAID);
        orders.setOrderStatus(Constants.ORDER_STATUS_UNPAID);
        orders.setReceiverName(dto.getReceiverName());
        orders.setReceiverPhone(dto.getReceiverPhone());
        orders.setReceiverAddress(dto.getReceiverAddress());
        orders.setRemark(dto.getRemark());
        ordersMapper.insert(orders);
        // 7.删除购物车
        cartMapper.deleteBatchByIds(cartsIdS, userId);

        return orders;
    }

    @Override
    public PageResult<Orders> myPage(Long userId, OrderQueryDTO query) {
        Page<Orders> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<Orders> ordersPage = ordersMapper.selectPage(page, new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .eq(query.getOrderStatus()!= null ,Orders::getOrderStatus,query.getOrderStatus()));
        if (ordersPage.getTotal() == 0) {
            return PageResult.of(ordersPage);
        }
        Page<Orders> orderItems = getOrderItems(ordersPage);
        return PageResult.of(orderItems);
    }

    @Override
    public Orders detail(Long userId, String orderNo) {
        List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo));
        Orders orders = ordersMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId));
        orders.setItems(orderItems);
        return orders;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long userId, String orderNo) {
        // 状态机校验不做在查询里，而是直接放进 UPDATE 的条件：
        // 只有"待付款"的行能被改。并发下两个支付请求同时到达，
        // 数据库行锁保证只有一个 rows=1，天然防重复支付。

        // 并发下的错误写法，判断订单状态，然后更新订单状态。
        /*Orders orders = ordersMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId));
        if (orders == null) {
            log.error("订单不存在");
            throw new BusinessException(ResultCode.ORDER_NOT_EXISTS);
        }*/


        // 判断和更新之间存在时间窗口。两个线程可以同时通过判断教研，引发并发问题，这个叫做竞态条件。
        /*if (Constants.PAY_STATUS_UNPAID.equals(orders.getPayStatus())) {
            int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                    .set(Orders::getPayStatus, Constants.PAY_STATUS_PAID)
                    .set(Orders::getOrderStatus, Constants.ORDER_STATUS_PAID)
                    .set(Orders::getPayTime, LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                    .eq(Orders::getOrderNo, orderNo)
                    .eq(Orders::getUserId, userId));
            if (rows == 0) {
                // 没改到行 = 订单不存在 / 不是待付款状态（已支付或已取消）
                throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
            }
        }*/
        // 并发下正确写法，使用SQL 语句实现状态机
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getPayStatus, Constants.PAY_STATUS_PAID)
                .set(Orders::getOrderStatus, Constants.ORDER_STATUS_PAID)
                .set(Orders::getPayTime, LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId)
                .eq(Orders::getOrderStatus, Constants.ORDER_STATUS_UNPAID));
        if (rows == 0) {
            // 没改到行 = 订单不存在 / 不是待付款状态（已支付或已取消）
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String orderNo) {
        // 业务规则：只要订单还没「已完成」就能取消 ——
        //   待付款(0) / 待发货(1) / 已发货(2) → 可以取消
        //   已完成(3) → 货已签收、钱已结清，不能取消
        //   已取消(4) → 不能重复取消
        //
        // 状态校验不写在 Java 的 if 里，而是塞进 UPDATE 的条件（.in(...)）：
        // 由数据库行锁保证原子性，并发重复点取消时只有第一个请求能改到行，
        // 其余 rows=0 直接报错 —— 否则库存会被反复回滚，越滚越多（负超卖）。
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getOrderStatus, Constants.ORDER_STATUS_CANCELED)
                .set(Orders::getCancelTime, LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId)
                .in(Orders::getOrderStatus,
                        Constants.ORDER_STATUS_UNPAID,
                        Constants.ORDER_STATUS_PAID,
                        Constants.ORDER_STATUS_SENT));
        if (rows == 0) {
            // 没改到行 = 订单不存在 / 已完成 / 已取消
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

        // 已支付的订单取消 = 退款：pay_status 由「已支付」转「已退款」。
        // 放在状态机校验之后读，拿到的就是取消这一刻的真实支付状态
        // （此时 orderStatus 已经是 4，pay() 不可能再把它改成已支付）。
        int update = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getPayStatus, Constants.PAY_STATUS_REFUND)
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId)
                .eq(Orders::getPayStatus, Constants.PAY_STATUS_PAID));
        if (update == 0){
            // 没改到行 = 订单不存在 / 已退款
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }


        // 回滚库存和销量：和改状态在同一个事务里，
        // 不能出现"状态改了库存没滚"或"库存滚了状态没改"这种半成品
        List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo));

        Map<Long, Integer> productIdQuantityMap = orderItems.stream()
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        OrderItem::getQuantity,
                        Integer::sum   // 同商品quantity累加
                ));

        for (Map.Entry<Long, Integer> entry : productIdQuantityMap.entrySet()) {
            productMapper.rollbackStock(entry.getKey(), entry.getValue());
        }
        // 注意：订单明细不删。这是下单时的商品快照，取消后详情页还要展示
        // "当时买了什么、多少钱"，删了详情页就空了
    }

    @Override
    public PageResult<Orders> adminPage(OrderQueryDTO query) {
        Page<Orders> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<Orders> ordersPage = ordersMapper.selectPageList(page,query );
        if (ordersPage.getRecords().size() == 0 ){
            return PageResult.of(ordersPage);
        }
        Page<Orders> orderItems = getOrderItems(ordersPage);
        return PageResult.of(orderItems);
    }


    @Override
    public Orders adminDetail(String orderNo) {
        Orders order = ordersMapper.selectBYOrderNo(orderNo);
        List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        order.setItems(orderItems);
        return order;
    }

    @Override
    public void adminUpdateStatus(String orderNo, Integer orderStatus) {
        int rows = ordersMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getOrderStatus, orderStatus)
                .eq(Orders::getOrderNo, orderNo));
        if (rows == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR);
        }

    }

    private Page<Orders> getOrderItems(Page<Orders> ordersPage) {
        List<Long> collect = ordersPage.getRecords().stream().map(Orders::getId).collect(Collectors.toList());
        List<OrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, collect));
        for (Orders orders : ordersPage.getRecords()) {
            orders.setItems(orderItems.stream()
                    .filter(item -> item.getOrderId().equals(orders.getId()))
                    .collect(Collectors.toList()));
        }
        return ordersPage;
    }
}
