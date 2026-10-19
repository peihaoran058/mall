package com.example.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mall.common.Constants;
import com.example.mall.common.ResultCode;
import com.example.mall.dto.LoginDTO;
import com.example.mall.entity.Admin;
import com.example.mall.exception.BusinessException;
import com.example.mall.mapper.AdminMapper;
import com.example.mall.service.AdminService;
import com.example.mall.util.MD5Util;
import com.example.mall.vo.LoginVO;
import com.fasterxml.jackson.databind.deser.std.UUIDDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AdminServiceImpl implements AdminService {

    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public LoginVO login(LoginDTO dto) {

        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, dto.getUsername()));
        if (admin == null){
            throw new BusinessException(ResultCode.ADMIN_LOGIN_ERROR,"用户名或密码错误");
        }
        if (!MD5Util.encrypt(dto.getPassword()).equals(admin.getPassword())){
            throw new BusinessException(ResultCode.ADMIN_LOGIN_ERROR,"用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(Constants.REDIS_LOGIN_ADMIN + token, admin
                , Constants.LOGIN_TOKEN_TTL, TimeUnit.SECONDS);

        return LoginVO.builder()
                .token(token)
                .id(admin.getId())
                .username(admin.getUsername())
                .nickname(admin.getNickname())
                .role(Constants.ROLE_ADMIN)
                .build();
    }
}
