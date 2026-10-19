package com.example.mall.service;

import com.example.mall.dto.CartAddDTO;
import com.example.mall.dto.CartQuantityDTO;
import com.example.mall.dto.CartSelectedDTO;
import com.example.mall.vo.CartItemVO;

import java.util.List;

/**
 * 购物车服务
 */
public interface CartService {


    void add(Long userId, CartAddDTO dto);

    void updateQuantity(Long userId, CartQuantityDTO dto);

    void updateSelected(Long userId, CartSelectedDTO dto);

    List<CartItemVO> list(Long userId);

    void delete(Long userId, Long cartId);

    void deleteByProductId(Long userId, Long productId);
}
