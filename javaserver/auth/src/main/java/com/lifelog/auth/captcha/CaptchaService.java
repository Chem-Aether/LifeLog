package com.lifelog.auth.captcha;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

/** 管理验证码的生成、暂存和一次性校验。 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    // 常量提取
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;

    private final ConcurrentMap<String, CaptchaEntry> captchas = new ConcurrentHashMap<>();

    /**
     * 生成验证码并在当前服务进程内存中暂存五分钟。
     * @return 验证码图片及其唯一校验键
     */
    public CaptchaWithKey generateCaptcha() {
        CaptchaUtil.Captcha captcha = CaptchaUtil.generateCaptcha();
        String captchaKey = UUID.randomUUID().toString();

        long expiresAt = System.currentTimeMillis() + CAPTCHA_EXPIRE_MINUTES * 60_000;
        captchas.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= System.currentTimeMillis());
        captchas.put(captchaKey, new CaptchaEntry(captcha.getText(), expiresAt));

        return new CaptchaWithKey(captcha, captchaKey);
    }

    /**
     * 校验并消费验证码；无论校验成功与否，该验证码均不可再次使用。
     * @param userInputCode 用户输入的验证码
     * @param captchaKey 生成验证码时返回的校验键
     * @return 验证码存在、未过期且内容匹配时返回 {@code true}
     */
    public boolean validateCaptcha(String userInputCode, String captchaKey) {
        if (userInputCode == null || captchaKey == null) {
            return false;
        }

        CaptchaEntry entry = captchas.remove(captchaKey);
        return entry != null && entry.expiresAt() > System.currentTimeMillis()
                && entry.code().equalsIgnoreCase(userInputCode.trim());
    }

    /** 封装验证码图片数据及其校验键。 */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CaptchaWithKey {
        /** 验证码文本及图片字节数据。 */
        private CaptchaUtil.Captcha captcha;
        /** 与验证码关联的唯一校验键。 */
        private String captchaKey;
    }

    /** 保存验证码内容及其过期时间。 */
    private record CaptchaEntry(String code, long expiresAt) {}
}
