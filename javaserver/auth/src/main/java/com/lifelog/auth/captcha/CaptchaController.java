package com.lifelog.auth.captcha;

import com.lifelog.auth.captcha.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 提供图形验证码获取接口。 */
@RestController
@RequestMapping("/captcha")
@Tag(name = "验证码模块", description = "图形验证码生成与验证接口")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * 生成验证码图片并通过响应头返回本次验证码的校验键。
     * @return JPEG 验证码图片及其校验键
     */
    @GetMapping(value = "/image", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "获取图形验证码图片")
    public ResponseEntity<byte[]> getCaptchaImage() {
        CaptchaService.CaptchaWithKey captchaWithKey = captchaService.generateCaptcha();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Captcha-Key", captchaWithKey.getCaptchaKey());
        headers.setContentType(MediaType.IMAGE_JPEG);

        return new ResponseEntity<>(
                captchaWithKey.getCaptcha().getImageBytes(),
                headers,
                HttpStatus.OK
        );
    }

}
