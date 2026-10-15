package com.lifelog.auth.user;

/** 封装修改当前用户密码所需的信息。 */
public class PasswordChangeRequest {
    /** 当前密码。 */
    private String currentPassword;
    /** 要设置的新密码。 */
    private String newPassword;

    /**
     * 获取当前密码。
     * @return 当前密码
     */
    public String getCurrentPassword() {
        return currentPassword;
    }

    /**
     * 设置当前密码。
     * @param currentPassword 当前密码
     */
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    /**
     * 获取新密码。
     * @return 新密码
     */
    public String getNewPassword() {
        return newPassword;
    }

    /**
     * 设置新密码。
     * @param newPassword 新密码
     */
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
