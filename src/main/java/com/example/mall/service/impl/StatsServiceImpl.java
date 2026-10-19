package com.example.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mall.entity.Orders;
import com.example.mall.entity.Product;
import com.example.mall.entity.ProductCategory;
import com.example.mall.entity.SysUser;
import com.example.mall.mapper.OrdersMapper;
import com.example.mall.mapper.ProductCategoryMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.mapper.SysUserMapper;
import com.example.mall.service.StatsService;
import com.example.mall.vo.StatsOverviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
public class StatsServiceImpl implements StatsService {

    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Override
    public StatsOverviewVO overview() {
        StatsOverviewVO statsOverviewVO = new StatsOverviewVO();


        statsOverviewVO.setUserCount(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)));
        statsOverviewVO.setProductCount(productMapper.selectCount(null));
        statsOverviewVO.setOnSaleProductCount(productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)));
        statsOverviewVO.setOrderCount(ordersMapper.selectCount(null));


        statsOverviewVO.setTodayOrderCount(ordersMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .ge(Orders::getCreateTime, LocalDate.now().atStartOfDay())));


        Orders orders = ordersMapper.selectOne(new QueryWrapper<Orders>()
                .select("sum(IFNULL(total_amount,0)) total_amount")
                .eq("pay_status", 1));
        BigDecimal totalAmount = null;
        if (orders != null) {
            totalAmount = orders.getTotalAmount();
        }else {
            totalAmount = BigDecimal.ZERO;
        }
        statsOverviewVO.setSalesAmount(totalAmount);

        statsOverviewVO.setCategoryCount(categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getStatus, 1)));

        statsOverviewVO.setLowStockCount(productMapper.selectCount(new LambdaQueryWrapper<Product>().le(Product::getStock, 10)));

        statsOverviewVO.setOrderStatusDist(ordersMapper.selectOrderStatusDist());

        statsOverviewVO.setOrderTrend(ordersMapper.selectOrderTrend());

        statsOverviewVO.setTopProducts(productMapper.selectTopProducts());


        return statsOverviewVO;
    }
}
