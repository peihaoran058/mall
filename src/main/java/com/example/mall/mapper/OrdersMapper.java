package com.example.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mall.dto.OrderQueryDTO;
import com.example.mall.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrdersMapper extends BaseMapper<Orders> {
    Page<Orders> selectPageList(@Param("page") Page<Orders> page, @Param("query") OrderQueryDTO query);

    @Select("select * from orders where order_no = #{orderNo}")
    Orders selectBYOrderNo(String orderNo);
}
