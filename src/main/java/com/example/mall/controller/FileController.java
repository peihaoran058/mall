package com.example.mall.controller;

import com.example.mall.common.Result;
import com.example.mall.util.FileUploadUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口（笔记 4.6）。
 *
 * <p>用 Postman/Apifox 测试时要注意：<b>选 form-data</b>，
 * key 填 {@code file}，类型选 File，否则 Spring 拿不到 MultipartFile，
 * 报 {@code Required request part 'file' is not present} —— 这也是个高频报错。</p>
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadUtil fileUploadUtil;

    /**
     * @param file   文件本身（form-data 的 key 必须是 file）
     * @param module 业务模块，作为子目录，例如 product / avatar
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "module", defaultValue = "common") String module) {
        return Result.success("上传成功", fileUploadUtil.upload(file, module));
    }
}
