package com.lifelog.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 绑定 JWT 密钥和令牌有效期配置。 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    private String secret;
    private long expire;

    /**
     * @return JWT 签名密钥
     */
    public String getSecret() {
        return secret;
    }

    /**
     * @param secret JWT 签名密钥
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * @return JWT 有效期，单位为秒
     */
    public long getExpire() {
        return expire;
    }

    /**
     * @param expire JWT 有效期，单位为秒
     */
    public void setExpire(long expire) {
        this.expire = expire;
    }
}
