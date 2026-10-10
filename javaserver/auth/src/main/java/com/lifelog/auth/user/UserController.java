package com.lifelog.auth.user;

import com.lifelog.common.api.Result;
import com.lifelog.auth.user.LoginRequest;
import com.lifelog.auth.user.User;
import com.lifelog.auth.captcha.CaptchaService;
import com.lifelog.auth.user.UserService;
import com.lifelog.auth.token.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Tag(name = "用户模块", description = "用户登录与首次账号初始化")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private JwtUtil jwtUtil;


    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ResponseEntity<Result> login(@RequestBody LoginRequest request) {
        try {
            if (!captchaService.validateCaptcha(request.getCaptchaCode(), request.getCaptchaKey())) {
                return new ResponseEntity<>(Result.error("验证码错误或已过期"), HttpStatus.UNAUTHORIZED);
            }
            User loginUser = userService.login(request.getAccount(), request.getPassword());
            String token = jwtUtil.createToken(loginUser.getUserId(), loginUser.getAccount());

            loginUser.setPassword(null);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("token", token);
            headers.set("Access-Control-Expose-Headers", "token");

            // 返回 200 + header + 数据
            return new ResponseEntity<>(
                    Result.success("登录成功", loginUser),
                    headers,
                    HttpStatus.OK
            );

        } catch (RuntimeException e) {
            // 登录失败 401
            return new ResponseEntity<>(
                    Result.error(e.getMessage()),
                    HttpStatus.UNAUTHORIZED
            );
        }
    }


    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public ResponseEntity<Result> register(@RequestBody LoginRequest request) {
        try {
            if (!captchaService.validateCaptcha(request.getCaptchaCode(), request.getCaptchaKey())) {
                return new ResponseEntity<>(Result.error("验证码错误或已过期"), HttpStatus.BAD_REQUEST);
            }
            User user = new User();
            user.setAccount(request.getAccount());
            user.setPassword(request.getPassword());
            userService.register(user);
            return ResponseEntity.ok(Result.success("注册成功"));
        } catch (RuntimeException e) {
            // 参数错误/账号已存在 400
            return new ResponseEntity<>(
                    Result.error(e.getMessage()),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

}
