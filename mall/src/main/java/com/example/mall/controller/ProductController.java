package com.example.mall.controller;

import com.example.mall.common.PageResult;
import com.example.mall.common.Result;
import com.example.mall.common.ResultCode;
import com.example.mall.dto.ProductQueryDTO;
import com.example.mall.entity.Product;
import com.example.mall.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口（用户端，第6、7课时）。
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 商品分页列表。
     * <p>查询条件用 GET + Query 参数，不要用 POST + body ——
     * 列表查询是"可缓存、可分享、可回退"的，用 GET 语义才正确。</p>
     */
    @GetMapping("/page")
    public Result<PageResult<Product>> page(ProductQueryDTO query) {
        return Result.success(productService.userPage(query));
    }

    /**
     * 商品详情（带 Redis 缓存）。
     * <p>课堂验证方法：连续调两次，看控制台 ——
     * 第一次打印 ">>> 缓存未命中，查询数据库"，第二次什么也不打印（直接走 Redis）。</p>
     */
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        Product product = productService.detail(id);
        if (product == null) {
            return Result.fail(ResultCode.PRODUCT_NOT_EXISTS);
        }
        return Result.success(product);
    }

    /**
     * 【缓存测试接口】带模式参数，方便课堂现场对比三种实现：
     * <pre>
     * /api/product/9999/cache-test?mode=unprotected  缓存穿透（每次都查库）
     * /api/product/9999/cache-test?mode=null-holder  缓存空对象（第二次直接返回空）
     * /api/product/1/cache-test?mode=mutex           缓存击穿互斥锁
     * </pre>
     * <p>注意 9999 这个 id 在数据库里<b>不存在</b>，专门用来演示穿透。</p>
     */
    @GetMapping("/{id}/cache-test")
    public Result<Product> cacheTest(@PathVariable Long id,
                                     @RequestParam(defaultValue = "unprotected") String mode) {
        Product product;
        switch (mode) {
            case "null-holder":
                product = productService.detailWithNullHolder(id);
                break;
            case "mutex":
                product = productService.detailWithMutex(id);
                break;
            case "unprotected":
            default:
                product = productService.detailUnprotected(id);
                break;
        }
        return Result.success("模式：" + mode, product);
    }
}
