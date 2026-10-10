package com.lifelog.auth.user;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@TableName("app_user")
public class User {
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    private String account;           // 账号
    private String userName;         // 姓名
    private String password;          // 密码
    private String role;              // 用户类型：超级管理员0；管理员1；普通用户2
}
