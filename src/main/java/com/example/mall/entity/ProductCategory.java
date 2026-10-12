package com.example.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品分类（对应表 product_category）。
 *
 * <p><b>课上重点：树形结构怎么存？</b><br>
 * 这里用最经典的 {@code parent_id} 自关联（邻接表）：</p>
 * <ul>
 *     <li>优点：结构简单、增删改方便、查直接子节点很快</li>
 *     <li>缺点：查整棵树要递归，层级深了 SQL 次数会爆炸</li>
 * </ul>
 *
 * <p>其他方案课上扩展：</p>
 * <ul>
 *     <li>路径枚举：加 {@code path} 字段存 {@code /1/11/}，查子孙一条 {@code like} 搞定</li>
 *     <li>左右值（嵌套集）：查询极快，但增删要改一大片数据</li>
 *     <li>闭包表：一张关系表存所有祖先-后代关系，最灵活但表会膨胀</li>
 * </ul>
 * <p>本项目只有两级分类，一次性把全表查出来在内存里组装是最优解
 * （<b>1 次 SQL</b>），而不是循环里递归查库（<b>N+1 次 SQL</b>）。</p>
 */
@Data
@TableName("product_category")
public class ProductCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类 id，0 表示一级分类 */
    private Long parentId;

    private String name;

    private String icon;

    /** 层级：1 一级，2 二级 */
    private Integer level;

    private Integer sort;

    /** 1 启用 0 停用 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 子分类列表，不映射数据库字段，只用于接口返回树形结构 */
    @TableField(exist = false)
    private List<ProductCategory> children;



}
