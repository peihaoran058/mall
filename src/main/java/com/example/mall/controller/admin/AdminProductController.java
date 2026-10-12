package com.example.mall.controller.admin;

import com.example.mall.common.PageResult;
import com.example.mall.common.Result;
import com.example.mall.dto.ProductQueryDTO;
import com.example.mall.dto.ProductSaveDTO;
import com.example.mall.entity.Product;
import com.example.mall.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 后台商品管理（第6课时）。
 */
@RestController
@RequestMapping("/api/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    /** 分页条件查询：名称模糊 + 分类 + 上下架状态 */
    @GetMapping("/page")
    public Result<PageResult<Product>> page(ProductQueryDTO query) {
        return Result.success(productService.adminPage(query));
    }

    /** 新增或修改商品（图片先调 /api/file/upload 拿到 URL 再传进来） */
    @PostMapping
    public Result<Product> save(@RequestBody @Valid ProductSaveDTO dto) {
        return Result.success(dto.getId() == null ? "新增成功" : "修改成功", productService.save(dto));
    }

    /** 上架 / 下架 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateStatus(id, status);
        return Result.success("操作成功", null);
    }

    /** 删除商品（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success("删除成功", null);
    }
}
