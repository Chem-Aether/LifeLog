package com.lifelog.auth.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifelog.auth.user.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 定义用户表的 MyBatis 数据访问操作。 */
public interface UserMapper extends BaseMapper<User> {
    /**
     * 根据账号查询用户。
     * @param account 用户账号
     * @return 匹配的用户；不存在时返回 {@code null}
     */
    @Select("SELECT * FROM app_user WHERE account = #{account}")
    User selectByAccount(String account);

    /**
     * 根据账号更新用户密码。
     * @param account 用户账号
     * @param password 待保存的密码哈希
     * @return 更新的记录数
     */
    @Update("UPDATE app_user SET password = #{password} WHERE account = #{account}")
    int updatePasswordByAccount(String account, String password);

    /**
     * 更新用户昵称及更新时间。
     * @param userId 用户 ID
     * @param userName 新昵称
     * @return 更新的记录数
     */
    @Update("UPDATE app_user SET user_name = #{userName}, update_time = now() WHERE user_id = #{userId}")
    int updateProfile(@Param("userId") Long userId, @Param("userName") String userName);

    /**
     * 根据用户 ID 更新密码及更新时间。
     * @param userId 用户 ID
     * @param password 待保存的密码哈希
     * @return 更新的记录数
     */
    @Update("UPDATE app_user SET password = #{password}, update_time = now() WHERE user_id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);
}
