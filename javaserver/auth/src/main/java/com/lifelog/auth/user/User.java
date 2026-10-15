package com.lifelog.auth.user;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@Data
@TableName("app_user")
/** 映射系统用户表的实体。 */
public class User {
    @TableId(value = "user_id", type = IdType.AUTO)
    /** 用户 ID。 */
    private Long userId;
    /** 登录账号。 */
    private String account;
    /** 用户姓名或昵称。 */
    private String userName;
    /** 用户密码，存储时使用 BCrypt 哈希。 */
    private String password;
    /** 用户类型：超级管理员 0、管理员 1、普通用户 2。 */
    private String role;
}
