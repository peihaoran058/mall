package com.example.mall.util;

import com.example.mall.common.Constants;
import com.example.mall.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MD5 加密工具。
 *
 * <p><b>课上重点（务必讲清楚，这是安全常识）：</b></p>
 * <ul>
 *     <li>MD5 是**摘要算法**不是加密算法，一旦拿到密文可以彩虹表反查</li>
 *     <li>加盐（salt）只能防简单的彩虹表，盐泄露就失效</li>
 *     <li>真正安全的是 BCrypt / Argon2 这类带工作因子的算法，Spring Security 的
 *         {@code BCryptPasswordEncoder} 可以直接用</li>
 * </ul>
 * 本项目用 MD5 只是为了和笔记保持一致、方便同学理解流程，<b>生产环境请换成 BCrypt</b>。
 */
public class MD5Util {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private MD5Util() {
    }

    /** 裸 MD5，返回 32 位小写十六进制 */
    public static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            char[] chars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                chars[i * 2] = HEX[b >>> 4];
                chars[i * 2 + 1] = HEX[b & 0x0F];
            }
            return new String(chars);
        } catch (Exception e) {
            // 正常情况不会发生（JDK 一定支持 MD5），但工具类里不要 throw Exception
            throw new BusinessException("密码加密失败：" + e.getMessage());
        }
    }

    /** 加盐 MD5：项目里存库统一用这个 */
    public static String encrypt(String rawPassword) {
        return md5(rawPassword + Constants.PASSWORD_SALT);
    }

    /**
     * 校验密码。
     * <p>注意要用 {@code equals} 而不是 {@code ==} 比较字符串——
     * {@code ==} 比的是引用地址，两个内容一样的字符串可能不相等。</p>
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return encrypt(rawPassword).equals(encodedPassword);
    }
}
