package com.lifelog.common.api;

import lombok.Getter;
import lombok.Setter;

/** 统一封装接口响应消息和数据。 */
@Getter
@Setter
public class Result {
    private String msg;
    private Object data;

    /** 创建空响应对象，仅供静态工厂方法使用。 */
    private Result() {}

    /**
     * 创建不包含业务数据的成功响应。
     * @return 成功响应
     */
    public static Result success() {
        return success("操作成功", null);
    }

    /**
     * @param data 响应数据
     * @return 成功响应
     */
    public static Result success(Object data) {
        return success("操作成功", data);
    }

    /**
     * @param msg 响应消息
     * @param data 响应数据
     * @return 成功响应
     */
    public static Result success(String msg, Object data) {
        Result result = new Result();
        result.msg = msg;
        result.data = data;
        return result;
    }

    /**
     * @param msg 错误消息
     * @return 错误响应
     */
    public static Result error(String msg) {
        return success(msg, null);
    }
}
