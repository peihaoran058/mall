package com.example.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.entity.ShoppingCart;
import com.example.mall.vo.CartItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {

    /**
     * 查询购物车列表并关联商品信息。
     *
     * <p><b>课上重点：为什么不给 shopping_cart 表建外键？</b><br>
     * 互联网项目普遍<b>不建物理外键</b>，原因：</p>
     * <ul>
     *     <li>外键检查会在父表上加锁，高并发下容易死锁、拖慢写入</li>
     *     <li>分库分表后外键直接失效，代码里还是得靠应用层保证</li>
     *     <li>数据迁移、批量导入时外键是巨大的麻烦</li>
     * </ul>
     * 关系由<b>应用层 + 索引</b>保证，join 用 {@code left join} 显式写出。
     */
    List<CartItemVO> selectCartListByUserId(@Param("userId") Long userId);

    /**
     * 批量删除购物车记录（下单成功后清空已结算的商品）。
     * <p>用 {@code <foreach>} 拼 in，注意集合为空时 SQL 会变成 {@code in ()} 直接报错，
     * 调用方必须先判空——这就是"参数为空的边界条件"。</p>
     */
    int deleteBatchByIds(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    CartItemVO selectStock(Long cartId);

    List<Long> selectProductListByUserId(Long userId);
}
