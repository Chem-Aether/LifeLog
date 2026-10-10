package com.lifelog.auth.user;

import lombok.Data;

@Data
public class LoginRequest {
    private String account;
    private String password;
    private String captchaCode;
    private String captchaKey;
}
