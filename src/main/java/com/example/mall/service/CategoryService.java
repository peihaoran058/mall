package com.example.mall.service;

import com.example.mall.dto.CategorySaveDTO;
import com.example.mall.entity.ProductCategory;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 分类服务
 */
public interface CategoryService {

    List<ProductCategory> tree(HttpServletRequest request);

    ProductCategory save(CategorySaveDTO dto);

    void delete(Long id);
}
