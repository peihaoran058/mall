package com.example.mall.service;

import com.example.mall.dto.LoginDTO;
import com.example.mall.vo.LoginVO;

import javax.servlet.http.HttpSession;

/**
 * 管理员服务。
 */
public interface AdminService {

    LoginVO login(LoginDTO dto);
}
