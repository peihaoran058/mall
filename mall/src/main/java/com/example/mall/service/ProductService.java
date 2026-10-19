package com.example.mall.service;

import com.example.mall.common.PageResult;
import com.example.mall.dto.ProductQueryDTO;
import com.example.mall.dto.ProductSaveDTO;
import com.example.mall.entity.Product;

/**
 * 商品服务
 */
public interface ProductService {

    PageResult<Product> userPage(ProductQueryDTO query);

    Product detail(Long id);

    Product detailWithNullHolder(Long id);

    Product detailWithMutex(Long id);

    Product detailUnprotected(Long id);

    PageResult<Product> adminPage(ProductQueryDTO query);

    Product save(ProductSaveDTO dto);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}