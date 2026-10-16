package com.lifelog.auth.user;

import com.lifelog.common.api.Result;
import com.lifelog.auth.captcha.CaptchaService;
import com.lifelog.auth.token.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.auth0.jwt.exceptions.JWTVerificationException;

/** 提供用户注册、登录和当前用户资料管理接口。 */
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


    /**
     * 校验验证码和用户凭据，成功后返回用户信息及 JWT。
     * @param request 登录请求
     * @return 登录结果；凭据无效时返回未授权响应
     */
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


    /**
     * 校验验证码并创建初始用户账号。
     * @param request 注册请求
     * @return 注册处理结果
     */
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

    /**
     * 根据请求头中的 JWT 获取当前用户资料。
     * @param token 请求头中的 JWT
     * @return 当前用户资料；令牌缺失或无效时返回未授权响应
     */
    @GetMapping("/me")
    @Operation(summary = "当前用户信息")
    public ResponseEntity<Result> me(@RequestHeader(value = "token", required = false) String token) {
        try {
            return ResponseEntity.ok(Result.success(userService.getProfile(accountFromToken(token))));
        } catch (RuntimeException e) {
            return new ResponseEntity<>(Result.error(e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 更新当前登录用户的昵称资料。
     * @param token 请求头中的 JWT
     * @param request 资料更新请求
     * @return 更新后的用户资料
     */
    @PutMapping("/me")
    @Operation(summary = "更新当前用户资料")
    public ResponseEntity<Result> updateProfile(@RequestHeader(value = "token", required = false) String token,
                                                @RequestBody ProfileUpdateRequest request) {
        try {
            if (request == null) throw new RuntimeException("请求数据不能为空");
            User user = userService.updateProfile(accountFromToken(token), request.getUserName());
            return ResponseEntity.ok(Result.success("资料已更新", user));
        } catch (RuntimeException e) {
            return new ResponseEntity<>(Result.error(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 校验当前密码并更新为新密码。
     * @param token 请求头中的 JWT
     * @param request 当前密码和新密码
     * @return 密码更新结果
     */
    @PutMapping("/me/password")
    @Operation(summary = "修改当前用户密码")
    public ResponseEntity<Result> changePassword(@RequestHeader(value = "token", required = false) String token,
                                                 @RequestBody PasswordChangeRequest request) {
        try {
            if (request == null) throw new RuntimeException("请求数据不能为空");
            userService.changePassword(accountFromToken(token), request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok(Result.success("密码已更新，请重新登录"));
        } catch (RuntimeException e) {
            return new ResponseEntity<>(Result.error(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 从 JWT 中提取账号，并将令牌错误转换为认证异常信息。
     * @param token 请求头中的 JWT
     * @return 令牌中的用户账号
     * @throws RuntimeException 令牌缺失或无效时抛出
     */
    private String accountFromToken(String token) {
        if (token == null || token.isBlank()) throw new RuntimeException("请先登录");
        try {
            return jwtUtil.getAccount(token);
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("登录已过期，请重新登录");
        }
    }

}
