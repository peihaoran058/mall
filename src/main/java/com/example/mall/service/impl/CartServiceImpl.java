package com.example.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mall.dto.CartAddDTO;
import com.example.mall.dto.CartQuantityDTO;
import com.example.mall.dto.CartSelectedDTO;
import com.example.mall.entity.ShoppingCart;
import com.example.mall.mapper.ProductMapper;
import com.example.mall.mapper.ShoppingCartMapper;
import com.example.mall.service.CartService;
import com.example.mall.vo.CartItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class CartServiceImpl implements CartService {


    @Autowired
    private ShoppingCartMapper cartMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, CartAddDTO dto) {

        ShoppingCart shoppingCart = cartMapper.selectOne(new LambdaQueryWrapper<ShoppingCart>()
                .eq(ShoppingCart::getProductId, dto.getProductId())
                .eq(ShoppingCart::getUserId,userId));
        if (shoppingCart == null){
            shoppingCart = new ShoppingCart();
            shoppingCart.setUserId(userId);
            shoppingCart.setProductId(dto.getProductId());
            shoppingCart.setQuantity(dto.getQuantity());
            cartMapper.insert(shoppingCart);
        }else {
            shoppingCart.setQuantity(shoppingCart.getQuantity() + dto.getQuantity());
            cartMapper.updateById(shoppingCart);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, CartQuantityDTO dto) {
        // 1. 查询商品库存
        CartItemVO cartItemVO = cartMapper.selectStock(dto.getCartId());
        // 2. 校验数量
        if (dto.getQuantity() > cartItemVO.getStock()) {
            log.error("商品 {} 库存不足，当前库存为 {}", cartItemVO.getProductId(), cartItemVO.getStock());
            throw new RuntimeException("商品" + cartItemVO.getProductName() + "库存不足，当前库存为" + cartItemVO.getStock());
        }else {
            ShoppingCart cart = new ShoppingCart();
            cart.setId(dto.getCartId());
            cart.setQuantity(dto.getQuantity());
            cartMapper.updateById(cart);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSelected(Long userId, CartSelectedDTO dto) {
        LambdaUpdateWrapper<ShoppingCart> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(ShoppingCart::getId, dto.getCartIds());
        wrapper.set(ShoppingCart::getSelected,dto.getSelected());
        cartMapper.update(null,wrapper);
    }

    @Override
    public List<CartItemVO> list(Long userId) {
        List<CartItemVO> cartItemVOS = cartMapper.selectCartListByUserId(userId);
        cartItemVOS.forEach(cartItemVO -> {
            cartItemVO.setTotalAmount(cartItemVO.getPrice().multiply(new BigDecimal(cartItemVO.getQuantity())));
        });
        return cartItemVOS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long cartId) {
        LambdaQueryWrapper<ShoppingCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShoppingCart::getId, cartId);
        wrapper.eq(ShoppingCart::getUserId, userId);
        cartMapper.delete(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByProductId(Long userId, Long productId) {
        LambdaQueryWrapper<ShoppingCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShoppingCart::getProductId, productId);
        wrapper.eq(ShoppingCart::getUserId, userId);
        cartMapper.delete(wrapper);
    }
}
