package com.example.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {

    /**
     * 按父 id 统计子分类数量 —— 删除分类前的前置校验。
     * <p>不要用 {@code selectCount} + Wrapper 写，这里演示 {@code @Select} 注解方式，
     * 简单 SQL 用注解比 XML 更轻量。</p>
     */
    @Select("select count(1) from product_category where parent_id = #{parentId} and deleted = 0")
    int countChildren(@Param("parentId") Long parentId);

    /**
     * 查询所有启用的分类（一次 SQL 拿到全表，在内存里组装树）。
     * <p>注意：这里手写了 {@code deleted = 0}，因为自定义 SQL 不会走逻辑删除自动拼接。</p>
     */
    @Select("select * from product_category where deleted = 0 and status = 1 order by sort asc, id asc")
    List<ProductCategory> selectAllEnabled();

    @Select("select * from product_category where deleted = 0 order by sort asc, id asc")
    List<ProductCategory> selectAll();
}
