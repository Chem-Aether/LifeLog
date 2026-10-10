package com.lifelog.auth.user;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lifelog.auth.user.User;
import com.lifelog.auth.user.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    @Autowired
    private UserMapper userMapper;

    // 登录逻辑
    public User login(String account, String password) {
        if (account == null || account.isBlank()) {
            throw new RuntimeException("账号不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new RuntimeException("密码不能为空");
        }

        User user = userMapper.selectByAccount(account);
        if (user == null) {
            throw new RuntimeException("账号不存在");
        }

        String storedPassword = user.getPassword();
        boolean hashed = storedPassword != null && storedPassword.startsWith("$2");
        boolean valid = hashed
                ? PASSWORD_ENCODER.matches(password, storedPassword)
                : password.equals(storedPassword);
        if (!valid) {
            throw new RuntimeException("密码错误");
        }
        if (!hashed) userMapper.updatePasswordByAccount(account, PASSWORD_ENCODER.encode(password));

        return user;
    }
    // 注册
    public User register(User user) {
        if (user == null) {
            throw new RuntimeException("请求数据不能为空");
        }
        if (user.getAccount() == null || user.getAccount().isBlank()) {
            throw new RuntimeException("账号不能为空");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new RuntimeException("密码不能为空");
        }

        if (userMapper.selectCount(null) > 0) throw new RuntimeException("系统已初始化，请联系管理员创建账号");
        if (user.getPassword().length() < 8) throw new RuntimeException("密码至少需要 8 位");
        user.setPassword(PASSWORD_ENCODER.encode(user.getPassword()));
        user.setRole("0");

        userMapper.insert(user);
        return user;
    }
}
