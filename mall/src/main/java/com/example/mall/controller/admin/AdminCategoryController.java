package com.example.mall.controller.admin;

import com.example.mall.common.Result;
import com.example.mall.dto.CategorySaveDTO;
import com.example.mall.entity.ProductCategory;
import com.example.mall.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

/**
 * 后台分类管理（第5课时）。
 */
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    /** 分类树（含停用的分类，后台要看到全部） */
    @GetMapping("/tree")
    public Result<List<ProductCategory>> tree(HttpServletRequest request) {
        return Result.success(categoryService.tree(request));
    }

    /** 新增或修改分类：id 为空是新增，有值是修改 */
    @PostMapping
    public Result<ProductCategory> save(@RequestBody @Valid CategorySaveDTO dto) {
        return Result.success(dto.getId() == null ? "新增成功" : "修改成功", categoryService.save(dto));
    }

    /** 删除分类 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success("删除成功", null);
    }
}
