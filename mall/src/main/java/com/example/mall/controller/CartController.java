package com.example.mall.controller;

import com.example.mall.common.Constants;
import com.example.mall.common.Result;
import com.example.mall.dto.CartAddDTO;
import com.example.mall.dto.CartQuantityDTO;
import com.example.mall.dto.CartSelectedDTO;
import com.example.mall.service.CartService;
import com.example.mall.vo.CartItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 购物车接口（第8、9课时）。
 *
 * <p>注意每个方法的第一件事都是拿 {@code userId}（来自拦截器），
 * 而不是从请求参数里取 —— 这样就从根本上杜绝了"操作别人购物车"的可能。</p>
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 加入购物车 */
    @PostMapping("/add")
    public Result<Void> add(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                           @RequestBody @Valid CartAddDTO dto) {
        cartService.add(userId, dto);
        return Result.success("已加入购物车", null);
    }

    /** 修改购买数量 */
    @PutMapping("/quantity")
    public Result<Void> updateQuantity(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                      @RequestBody @Valid CartQuantityDTO dto) {
        cartService.updateQuantity(userId, dto);
        return Result.success("修改成功", null );
    }

    /**
     * 批量勾选 / 取消勾选。
     * <p>结算只结算 {@code selected=1} 的记录，所以"全选 / 反选"必须能落库，
     * 否则前端选中了、后端还是按旧的勾选状态下单，金额对不上。</p>
     */
    @PutMapping("/selected")
    public Result<Void> updateSelected(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                      @RequestBody @Valid CartSelectedDTO dto) {
        cartService.updateSelected(userId, dto);
        return Result.success("操作成功", null);
    }

    /** 购物车列表 */
    @GetMapping("/list")
    public Result<List<CartItemVO>> list(@RequestAttribute(Constants.REQ_USER_ID) Long userId) {
        return Result.success(cartService.list(userId));
    }

    /** 删除购物车条目 */
    @DeleteMapping("/{cartId}")
    public Result<Void> delete(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                              @PathVariable Long cartId) {
        cartService.delete(userId, cartId);
        return Result.success("已删除", null);
    }

    /** 按商品 id 移出购物车（商品详情页用） */
    @DeleteMapping("/product/{productId}")
    public Result<Void> deleteByProduct(@RequestAttribute(Constants.REQ_USER_ID) Long userId,
                                       @PathVariable Long productId) {
        cartService.deleteByProductId(userId, productId);
        return Result.success("已移出购物车", null);
    }
}
