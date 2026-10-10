package com.lifelog.auth.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifelog.auth.user.User;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM app_user WHERE account = #{account}")
    User selectByAccount(String account);

    @Update("UPDATE app_user SET password = #{password} WHERE account = #{account}")
    int updatePasswordByAccount(String account, String password);
}
