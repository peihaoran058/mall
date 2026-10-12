package com.example.mall.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页返回对象。
 *
 * <p>不直接把 MyBatis-Plus 的 {@code IPage} 丢给前端，是为了"接口解耦"：
 * 以后换成 ES 分页、MongoDB 分页，前端不需要改任何代码。</p>
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private Long total;

    /** 总页数 */
    private Long pages;

    /** 当前页，从 1 开始 */
    private Long current;

    /** 每页条数 */
    private Long size;

    /** 当前页数据 */
    private List<T> records;

    public PageResult() {
        this.total = 0L;
        this.pages = 0L;
        this.current = 1L;
        this.size = 10L;
        this.records = new ArrayList<>();
    }

    public PageResult(Long total, Long pages, Long current, Long size, List<T> records) {
        this.total = total;
        this.pages = pages;
        this.current = current;
        this.size = size;
        this.records = records == null ? new ArrayList<>() : records;
    }

    /**
     * 把 MyBatis-Plus 的分页对象转换成项目自己的分页对象。
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 已经查好的 List + 总数，手工组装分页对象。
     */
    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(total, pages, current, size, records);
    }
}
