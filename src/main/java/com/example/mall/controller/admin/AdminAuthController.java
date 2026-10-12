package com.example.mall.controller.admin;


import com.example.mall.common.Result;
import com.example.mall.dto.LoginDTO;
import com.example.mall.service.AdminService;
import com.example.mall.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {


    @Autowired
    private AdminService adminService;


    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto){
        return Result.success("管理员登录成功",adminService.login(dto));
    }

    @GetMapping("/hello")
    public Result<String> hello(){
        return Result.success("哈哈哈");
    }

}
