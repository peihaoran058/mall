package com.example.mall.controller;

import com.example.mall.common.Result;
import com.example.mall.entity.ProductCategory;
import com.example.mall.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 分类接口（用户端，第5课时）。
 */
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 首页分类树。
     * <p>这个接口<b>不需要登录</b>（在 WebMvcConfig 里放行了），
     * 用户一进首页就要看分类。</p>
     */
    @GetMapping("/tree")
    public Result<List<ProductCategory>> tree(HttpServletRequest request) {
        return Result.success(categoryService.tree(request));
    }
}
