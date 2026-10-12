package com.example.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class CategorySaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新增时为空，修改时必传 */
    private Long id;

    /** 父分类 id，0 或 null 表示一级分类 */
    private Long parentId;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 20, message = "分类名称最长 20 个字符")
    private String name;

    private String icon;

    private Integer sort;

    private Integer status;
}
