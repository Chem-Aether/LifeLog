package com.lifelog.auth.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifelog.auth.user.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM app_user WHERE account = #{account}")
    User selectByAccount(String account);

    @Update("UPDATE app_user SET password = #{password} WHERE account = #{account}")
    int updatePasswordByAccount(String account, String password);

    @Update("UPDATE app_user SET user_name = #{userName}, update_time = now() WHERE user_id = #{userId}")
    int updateProfile(@Param("userId") Long userId, @Param("userName") String userName);

    @Update("UPDATE app_user SET password = #{password}, update_time = now() WHERE user_id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);
}
