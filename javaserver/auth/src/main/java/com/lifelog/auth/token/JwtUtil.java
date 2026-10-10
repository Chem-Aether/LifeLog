package com.lifelog.auth.token;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.lifelog.common.config.JwtConfig;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
public class JwtUtil {
    private final JwtConfig jwtConfig;

    public JwtUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    // 生成 token
    public String createToken(Long userId, String account) {
        long expireTime = jwtConfig.getExpire() * 1000;

        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("account", account)
                .withExpiresAt(new Date(System.currentTimeMillis() + expireTime))
                .sign(Algorithm.HMAC256(jwtConfig.getSecret()));
    }

    // 验证 token
    public DecodedJWT verifyToken(String token) {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(jwtConfig.getSecret())).build();
        return verifier.verify(token);
    }

    // 从token获取用户ID
    public Long getUserId(String token) {
        return verifyToken(token).getClaim("userId").asLong();
    }

    // 获取账号
    public String getAccount(String token) {
        return verifyToken(token).getClaim("account").asString();
    }
}
