package com.example.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.mall.dto.ProductQueryDTO;
import com.example.mall.entity.Product;
import com.example.mall.vo.ChartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 后台商品分页条件查询（自定义 XML + 分页插件结合）。
     *
     * <p><b>课上重点：</b>分页插件要生效，自定义方法必须满足两个条件：</p>
     * <ol>
     *     <li>{@code IPage} 类型的参数放在<b>第一个</b>位置（或用 {@code @Param("page")} 标注）</li>
     *     <li>返回类型也必须是 {@code IPage}</li>
     * </ol>
     * 顺序放错，插件不会报错，只是默默不分页，返回全量数据。</p>
     */
    IPage<Product> selectAdminPage(IPage<Product> page, @Param("query") ProductQueryDTO query);

    /**
     * 【超卖修复的关键 SQL】带库存判断的原子扣减。
     *
     * <p>把"判断库存 &gt;= 数量"和"扣减库存"合并在一条 UPDATE 里，
     * 利用 InnoDB 行锁保证原子性。返回受影响行数：
     * 返回 1 说明扣减成功，返回 0 说明库存不足。</p>
     *
     * <p><b>对比第13课时的错误写法：</b><br>
     * 错误写法是先 {@code select stock} 拿到 10，再 {@code update set stock = 9}。
     * 两个线程可能都读到 10，都写 9，结果卖了 2 件库存只减了 1 —— 这就是超卖。</p>
     */
    @Update("update product set stock = stock - #{quantity}, sales = sales + #{quantity} " +
            "where id = #{productId} and stock >= #{quantity} and deleted = 0")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 取消订单时回滚库存。
     * <p>这里<b>不加</b> {@code stock >= #{quantity}} 判断，
     * 因为回滚是"必须成功"的操作，加判断反而可能在异常数据下静默失败。</p>
     */
    @Update("update product set stock = stock + #{quantity}, sales = sales - #{quantity} " +
            "where id = #{productId} and deleted = 0")
    int rollbackStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 查询库存（不带任何锁）。
     * <p>这是一个"看起来没问题其实很危险"的方法，专门留给课堂演示
     * "先查后改"的竞态条件。</p>
     */
    @Select("select stock from product where id = #{productId}")
    Integer selectStock(@Param("productId") Long productId);

    /**
     * 【错误示范专用】无任何条件判断的扣减，用来复现"库存变负数"。
     * <p>生产代码里绝不能出现，仅供第13课时演示使用。</p>
     */
    @Update("update product set stock = stock - #{quantity}, sales = sales + #{quantity} where id = #{productId}")
    int deductStockWithoutCheck(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    List<ChartItem> selectTopProducts();

}
