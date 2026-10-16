package com.lifelog.auth.user;

import lombok.Data;

/** 封装登录或注册请求中的账号、密码及验证码信息。 */
@Data
public class LoginRequest {
    /** 用户账号。 */
    private String account;
    /** 用户密码。 */
    private String password;
    /** 用户输入的图形验证码。 */
    private String captchaCode;
    /** 获取验证码时返回的校验键。 */
    private String captchaKey;
}
