package com.lifelog.auth.user;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 处理用户认证、账号初始化和个人资料业务。 */
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    @Autowired
    private UserMapper userMapper;

    /**
     * 校验账号密码并返回用户；兼容的明文密码会在登录成功后升级为 BCrypt 哈希。
     * @param account 用户账号
     * @param password 用户输入的密码
     * @return 认证成功的用户实体
     * @throws RuntimeException 输入缺失、账号不存在或密码错误时抛出
     */
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
    /**
     * 创建系统首个用户账号，并以 BCrypt 哈希形式保存密码。
     * @param user 待注册的用户信息
     * @return 已写入数据库的用户实体
     * @throws RuntimeException 注册数据无效或系统已完成初始化时抛出
     */
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

    /**
     * 查询用户资料并清除密码字段后返回。
     * @param account 用户账号
     * @return 不包含密码的用户资料
     * @throws RuntimeException 账号不存在时抛出
     */
    public User getProfile(String account) {
        User user = userMapper.selectByAccount(account);
        if (user == null) throw new RuntimeException("账号不存在");
        user.setPassword(null);
        return user;
    }

    /**
     * 更新用户昵称并返回不含密码的用户资料。
     * @param account 用户账号
     * @param userName 新昵称；{@code null} 将按空字符串处理
     * @return 更新后的用户资料
     * @throws RuntimeException 账号不存在或昵称超长时抛出
     */
    public User updateProfile(String account, String userName) {
        User user = userMapper.selectByAccount(account);
        if (user == null) throw new RuntimeException("账号不存在");
        String normalizedName = userName == null ? "" : userName.trim();
        if (normalizedName.length() > 50) throw new RuntimeException("昵称不能超过 50 个字符");
        userMapper.updateProfile(user.getUserId(), normalizedName);
        user.setUserName(normalizedName);
        user.setPassword(null);
        return user;
    }

    /**
     * 校验当前密码并保存新密码的 BCrypt 哈希。
     * @param account 用户账号
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     * @throws RuntimeException 密码缺失、不符合要求或当前密码错误时抛出
     */
    public void changePassword(String account, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()) throw new RuntimeException("请输入当前密码");
        if (newPassword == null || newPassword.isBlank()) throw new RuntimeException("请输入新密码");
        if (newPassword.length() < 8) throw new RuntimeException("新密码至少需要 8 位");
        if (currentPassword.equals(newPassword)) throw new RuntimeException("新密码不能与当前密码相同");

        User user = login(account, currentPassword);
        userMapper.updatePassword(user.getUserId(), PASSWORD_ENCODER.encode(newPassword));
    }
}
