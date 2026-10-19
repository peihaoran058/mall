package com.example.mall.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mall.common.Constants;
import com.example.mall.common.PageResult;
import com.example.mall.dto.ProductQueryDTO;
import com.example.mall.dto.ProductSaveDTO;
import com.example.mall.entity.Product;
import com.example.mall.entity.ProductCategory;
import com.example.mall.mapper.ProductCategoryMapper;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.service.ProductService;
import jdk.nashorn.internal.ir.CallNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class productServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductCategoryMapper categoryMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private  StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<Product> userPage(ProductQueryDTO dto) {

        Page<Product> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (dto.getCategoryId() != null) {
            List<ProductCategory> productCategories = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                    .eq(ProductCategory::getParentId, dto.getCategoryId()));
            List<Long> collect = productCategories.stream()
                    .filter(c -> c.getId() != null)
                    .map(ProductCategory::getId)
                    .collect(Collectors.toList());
            collect.add(dto.getCategoryId());
            wrapper.in(Product::getCategoryId, collect);
        }
        wrapper.like(dto.getName() != null && !dto.getName().isEmpty(),
                Product::getName, dto.getName());
        wrapper.ge(dto.getMinPrice() != null, Product::getPrice, dto.getMinPrice());
        wrapper.le(dto.getMaxPrice() != null, Product::getPrice, dto.getMaxPrice());
        if (dto.getSortBy() != null && dto.getOrderBy() != null) {
            if (dto.getSortBy().equals("price")){
                wrapper.orderBy(true, "asc".equals(dto.getOrderBy()),Product::getPrice);
            }
            if (dto.getSortBy().equals("sales")){
                wrapper.orderBy(true, "asc".equals(dto.getOrderBy()),Product::getSales);
            }
            if (dto.getSortBy().equals("id")) {
                wrapper.orderBy(true, "asc".equals(dto.getOrderBy()), Product::getId);
            }
        }else{
            wrapper.orderByDesc(Product::getCreateTime);
        }
        Page<Product> productPage = productMapper.selectPage(page, wrapper);
        return PageResult.of(productPage);
    }

    @Override
    public Product detail(Long id) {
        Product o = (Product)redisTemplate.opsForValue().get(Constants.REDIS_PRODUCT_DETAIL + id);
        if (o != null){
            return o;
        }

        Product product = productMapper.selectById(id);
        redisTemplate.opsForValue().set(Constants.REDIS_PRODUCT_DETAIL + id, product, 60, TimeUnit.SECONDS);
        return product;

    }


    /**
     * 缓存空对象，解决缓存穿透问题。
     */
    @Override
    public Product detailWithNullHolder(Long id) {
        Object o = redisTemplate.opsForValue().get(Constants.REDIS_PRODUCT_DETAIL + id);
        if (o != null && o instanceof Product){
            return (Product) o;
        }
        if (o != null && o instanceof String){
            log.info(">>> 缓存命中，返回空对象");
            return null;
        }
        Product product = productMapper.selectById(id);
        if (product == null){
            redisTemplate.opsForValue().set(Constants.REDIS_PRODUCT_DETAIL + id,"NUll", 60, TimeUnit.SECONDS);
            return null;
        }else {
            redisTemplate.opsForValue().set(Constants.REDIS_PRODUCT_DETAIL + id, product, 60, TimeUnit.SECONDS);
            return product;
        }
    }


    @Override
    public Product detailWithMutex(Long id) {
        String lockKey = Constants.REDIS_LOCK_CACHE_REBUILD + id;
        String cacheKey = Constants.REDIS_PRODUCT_DETAIL  + id;

        // 查询
        Object o = redisTemplate.opsForValue().get(cacheKey);
        if(o == null){
            try{
                // 缓存未命中，尝试获取锁
                Boolean locked = stringRedisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
                if (locked){
                    // 获取锁成功，再次查询缓存
                    o= redisTemplate.opsForValue().get(cacheKey);
                    if (o != null){
                        return (Product)o;
                    }
                    // 查询数据库
                    Product product = productMapper.selectById(id);
                    // 把结果放在缓冲中
                    redisTemplate.opsForValue().set(cacheKey, product, 60, TimeUnit.SECONDS);
                    return product;
                }
                Thread.sleep(100);
                o = redisTemplate.opsForValue().get(cacheKey);
                if (o instanceof Product) {
                    return (Product) o;
                }
                // 兜底：等待后还是没有，直接查库返回（保证接口可用性优先）
                return productMapper.selectById(id);

            }catch (Exception e){
                log.error(">>> 缓存重建异常", e);
            }finally {
                redisTemplate.delete(lockKey);
            }


        }
        return (Product)o;
    }

    /**
     * 不带带缓存的查询方法，缓存穿透。
     */
    @Override
    public Product detailUnprotected(Long id) {
        return productMapper.selectById(id);
    }

    @Override
    public PageResult<Product> adminPage(ProductQueryDTO query) {
        Page<Product> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(query.getName() != null && !query.getName().isEmpty(),
                Product::getName, query.getName());
        List<ProductCategory> productCategories = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, query.getCategoryId()));
        List<Long> collect = productCategories.stream()
                .filter(c -> c.getId() != null)
                .map(ProductCategory::getId)
                .collect(Collectors.toList());
        collect.add(query.getCategoryId());
        wrapper.in(query.getCategoryId() != null, Product::getCategoryId, collect);
        wrapper.eq(query.getStatus() != null, Product::getStatus, query.getStatus());
        return PageResult.of(productMapper.selectPage(page, wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product save(ProductSaveDTO dto) {
        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        if (dto.getId() == null){
            productMapper.insert(product);
        }else {
            productMapper.updateById(product);
        }
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        productMapper.updateById(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        productMapper.deleteById(id);
    }
}
