package com.lifelog.common.api;

public class Result {
    private String msg;
    private Object data;

    private Result() {}

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public static Result success() {
        return success("操作成功", null);
    }

    public static Result success(Object data) {
        return success("操作成功", data);
    }

    public static Result success(String msg, Object data) {
        Result result = new Result();
        result.msg = msg;
        result.data = data;
        return result;
    }

    public static Result error(String msg) {
        return success(msg, null);
    }
}
