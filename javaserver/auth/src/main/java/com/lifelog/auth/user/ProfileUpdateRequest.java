package com.lifelog.auth.user;

/** 封装更新当前用户资料的请求数据。 */
public class ProfileUpdateRequest {
    /** 用户昵称。 */
    private String userName;

    /**
     * 获取用户昵称。
     * @return 用户昵称
     */
    public String getUserName() {
        return userName;
    }

    /**
     * 设置用户昵称。
     * @param userName 用户昵称
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }
}
