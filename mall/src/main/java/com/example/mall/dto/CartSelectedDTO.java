package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 购物车勾选/取消勾选入参。
 *
 * <p><b>课上重点：为什么设计成"传一批 id"而不是"一次一个"？</b></p>
 * <p>"全选"这个操作如果有 20 件商品，前端逐个调接口就是 20 次 HTTP 请求，
 * 每个请求还要各自开事务 —— 慢、而且一旦中间失败，会出现
 * "选了一半"的中间状态，用户看到的总价还是错的。</p>
 *
 * <p>批量接口一次事务搞定，要么全成要么全败，语义清晰。</p>
 *
 * <p>另外注意：这里只有 {@code cartIds}，<b>没有 userId</b>。
 * 用户身份必须由后端从 token 里取 —— 如果放在请求体里让前端传，
 * 攻击者改一下 userId 就能操作别人的购物车（水平越权）。</p>
 */
@Data
public class CartSelectedDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 要操作的购物车条目 id 列表（全选时传当前页全部 id） */
    @NotEmpty(message = "请至少选择一条购物车记录")
    private List<Long> cartIds;

    /** 1 勾选，0 取消勾选 */
    @NotNull(message = "勾选状态不能为空")
    private Integer selected;
}
