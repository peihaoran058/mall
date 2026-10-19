package com.example.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mall.common.Constants;
import com.example.mall.common.ResultCode;
import com.example.mall.dto.CategorySaveDTO;
import com.example.mall.entity.Product;
import com.example.mall.entity.ProductCategory;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.ProductCategoryMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.service.CategoryService;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private ProductCategoryMapper categoryMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ProductMapper productMapper;


    /**
     * 分类树。
     *  1. 先把一级分类查询出来，再循环查询各自的二级分类。 1+N的问题
     *  2. 把所有的分类查询出来，在后端进行分类树的组装。
     *
     */
    @Override
    public List<ProductCategory> tree(HttpServletRequest request) {

        Object role = request.getAttribute(Constants.REQ_ROLE);
        List<ProductCategory> all = new ArrayList<>();
        if (role != null && Constants.ROLE_ADMIN.equals(role)){
            all = categoryMapper.selectAll();
        }

        // 商城界面
        if(role == null || Constants.ROLE_USER.equals(role)){
            Object o = redisTemplate.opsForValue().get(Constants.REDIS_CATEGORY_TREE);
            if (o != null){
                log.info("从缓存中获取分类树");
                return (List<ProductCategory>) o;
            }else {
                all = categoryMapper.selectAllEnabled();
            }

        }
        // 按 parentId 分组
        Map<Long, List<ProductCategory>> childrenMap = all.stream()
                .collect(Collectors.groupingBy(ProductCategory::getParentId));

        // 组装树：只返回一级分类，一级分类带上 children
        List<ProductCategory> tree = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0L)
                .peek(parent -> parent.setChildren(
                        childrenMap.getOrDefault(parent.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
        redisTemplate.opsForValue().set(Constants.REDIS_CATEGORY_TREE, tree,7*24*60*60, TimeUnit.SECONDS);
        return tree;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductCategory save(CategorySaveDTO dto) {
        ProductCategory category = new ProductCategory();
        if (dto.getId() == null){
            BeanUtils.copyProperties(dto, category);
            if (dto.getParentId() == null || dto.getParentId() == 0){
                category.setLevel(1);
            }else {
                category.setLevel(2);
            }
            categoryMapper.insert(category);
        }else {
            BeanUtils.copyProperties(dto, category);
            categoryMapper.updateById(category);
        }
        redisTemplate.delete(Constants.REDIS_CATEGORY_TREE);
        return category;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProductCategory productCategory = categoryMapper.selectById(id);
        if (productCategory != null && productCategory.getLevel() == 1){
            Long l = categoryMapper.selectCount(
                    new LambdaQueryWrapper<ProductCategory>()
                            .eq(ProductCategory::getParentId, id));
            if (l > 0){
                throw new BusinessException(ResultCode.CATEGORY_HAS_CHILDREN);
            }
            categoryMapper.deleteById(id);
        }else{
            Long l = productMapper.selectCount(
                    new LambdaQueryWrapper<Product>()
                            .eq(Product::getCategoryId, id));
            if (l > 0){
                throw new BusinessException(ResultCode.CATEGORY_HAS_PRODUCT);
            }
            categoryMapper.deleteById(id);
        }
        redisTemplate.delete(Constants.REDIS_CATEGORY_TREE);
    }
}
