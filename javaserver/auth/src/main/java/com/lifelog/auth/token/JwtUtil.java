package com.lifelog.auth.token;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.lifelog.common.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Date;

/** 创建并校验身份认证服务使用的 JWT。 */
@Component
@RequiredArgsConstructor
public class JwtUtil {
    private final JwtConfig jwtConfig;

    /**
     * 创建包含用户标识和账号声明、带有效期的签名令牌。
     * @param userId 用户 ID
     * @param account 用户账号
     * @return 签名后的 JWT 字符串
     */
    public String createToken(Long userId, String account) {
        long expireTime = jwtConfig.getExpire() * 1000;

        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("account", account)
                .withExpiresAt(new Date(System.currentTimeMillis() + expireTime))
                .sign(Algorithm.HMAC256(jwtConfig.getSecret()));
    }

    /**
     * 验证令牌签名和有效期，并解析令牌内容。
     * @param token 待验证的 JWT 字符串
     * @return 已验证的令牌
     * @throws com.auth0.jwt.exceptions.JWTVerificationException 令牌无效或已过期时抛出
     */
    public DecodedJWT verifyToken(String token) {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(jwtConfig.getSecret())).build();
        return verifier.verify(token);
    }

    /**
     * 从令牌中读取用户 ID。
     * @param token 待解析的 JWT 字符串
     * @return 令牌中的用户 ID
     */
    public Long getUserId(String token) {
        return verifyToken(token).getClaim("userId").asLong();
    }

    /**
     * 从令牌中读取用户账号。
     * @param token 待解析的 JWT 字符串
     * @return 令牌中的用户账号
     */
    public String getAccount(String token) {
        return verifyToken(token).getClaim("account").asString();
    }
}
