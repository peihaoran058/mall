package com.example.mall.service;

import com.example.mall.common.PageResult;
import com.example.mall.dto.UserQueryDTO;
import com.example.mall.vo.UserVO;

/**
 * 后台用户管理服务
 */
public interface AdminUserService {

    /** 后台用户分页列表（关键字 / 状态可选）。 */
    PageResult<UserVO> page(UserQueryDTO query);

    /** 启用 / 禁用用户。 */
    void updateStatus(Long userId, Integer status);

}
