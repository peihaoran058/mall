package com.example.mall.util;

import com.example.mall.common.Constants;
import com.example.mall.config.MallProperties;
import com.example.mall.exception.BusinessException;
import com.example.mall.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地文件上传工具（笔记 4.6）。
 *
 * <p><b>课上重点（每一个都是真实踩过的坑）：</b></p>
 * <ol>
 *     <li><b>不要用原始文件名存盘</b>：用户上传 {@code ../../etc/passwd} 就是路径穿越攻击，
 *         而且中文文件名在不同系统上会乱码。这里只取后缀，主名用 UUID。</li>
 *     <li><b>校验放在服务端</b>：前端 {@code accept="image/*"} 只是体验优化，绕过它太容易了。</li>
 *     <li><b>按日期分目录</b>：单目录文件几十万时，{@code ls} 都要卡半天。</li>
 *     <li><b>生产环境要用对象存储</b>（OSS/COS/MinIO）：本地磁盘在容器里一重启就没了，
 *         多台机器之间也不共享。本项目为了课堂演示才写本地磁盘。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadUtil {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MallProperties mallProperties;

    public String upload(MultipartFile file) {
        return upload(file, "common");
    }

    /**
     * @param file   上传的文件
     * @param module 业务模块名，会作为一级子目录，例如 product / avatar
     * @return 可访问的 URL
     */
    public String upload(MultipartFile file, String module) {
        if (file == null){
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }

        // 校验文件后缀（目前只允许上传图片）
        String ext = getExtension(file.getOriginalFilename());
        if (!isAllowedImage(ext)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只允许上传图片");
        }

        // 创建目录：{根目录}/{模块}/{yyyyMMdd}/
        String dateDir = LocalDate.now().format(DATE_DIR);
        String dir = mallProperties.getUpload().getPath()+ File.separator + module +File.separator + dateDir;
        System.out.println("================"+dir);
        File fileDir = new File(dir);
        if (!fileDir.exists() ) {
            boolean created = fileDir.mkdirs();
            log.info("[上传目录] " + dir + (created ? " 创建成功" : " 已存在或创建失败"));
        }

        // 重新命名文件名：UUID + 后缀，彻底避开中文名与路径穿越
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        File dest = new File(fileDir, fileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "文件上传失败");
        }

        String url = mallProperties.getUpload().getUrlPrefix() + module + "/" + dateDir + "/" + fileName;
        log.info("文件上传成功 | size={}KB | url={}", file.getSize() / 1024, url);

        return url;

    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedImage(String ext) {
        for (String allowed : Constants.ALLOWED_IMAGE_EXT) {
            if (allowed.equals(ext)) {
                return true;
            }
        }
        return false;
    }
}
