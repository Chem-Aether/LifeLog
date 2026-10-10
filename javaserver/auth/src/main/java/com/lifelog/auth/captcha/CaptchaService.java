package com.lifelog.auth.captcha;

import com.lifelog.auth.captcha.CaptchaUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    // 常量提取
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;

    private final ConcurrentMap<String, CaptchaEntry> captchas = new ConcurrentHashMap<>();

    /**
     * 生成验证码，短时保存在当前服务进程内存中
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
     * 校验验证码
     */
    public boolean validateCaptcha(String userInputCode, String captchaKey) {
        if (userInputCode == null || captchaKey == null) {
            return false;
        }

        CaptchaEntry entry = captchas.remove(captchaKey);
        return entry != null && entry.expiresAt() > System.currentTimeMillis()
                && entry.code().equalsIgnoreCase(userInputCode.trim());
    }

    /**
     * 验证码返回对象（标准静态内部类 + Lombok 简化）
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CaptchaWithKey {
        private CaptchaUtil.Captcha captcha;
        private String captchaKey;
    }

    private record CaptchaEntry(String code, long expiresAt) {}
}
