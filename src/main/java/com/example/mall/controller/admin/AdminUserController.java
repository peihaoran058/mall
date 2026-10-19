package com.example.mall.controller.admin;

import com.example.mall.common.PageResult;
import com.example.mall.common.Result;
import com.example.mall.dto.UserQueryDTO;
import com.example.mall.service.AdminUserService;
import com.example.mall.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台用户管理（第17课时扩展）。
 *
 * <p>整个 {@code /api/admin/**} 都走 {@code AdminInterceptor} 校验角色，
 * 所以这里不需要再写任何登录判断 —— 权限是"配置"出来的，不是每个接口各写一遍。</p>
 */
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户分页列表 */
    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(UserQueryDTO query) {
        return Result.success(adminUserService.page(query));
    }

    /** 启用 / 禁用用户：status = 1 启用，0 禁用 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminUserService.updateStatus(id, status);
        return Result.success(status == 1 ? "已启用" : "已禁用", null);
    }
}
