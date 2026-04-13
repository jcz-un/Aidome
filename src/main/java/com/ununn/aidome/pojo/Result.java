package com.ununn.aidome.pojo;


import lombok.Data;

@Data
public class Result<T> {


    private Integer code; //编码：1成功，0和其它数字为失败
    private String msg; //错误信息
    private T data; //数据

    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = 1;
        return result;
    }

    public static <T> Result<T> success(T object) {
        Result<T> result = new Result<T>();
        result.data = object;
        result.code = 1;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<T>();
        result.msg = msg;
        result.code = 0;
        return result;
    }
    
    /**
     * 带错误码的失败响应
     * @param code 错误码（HTTP状态码或业务错误码）
     * @param msg 错误消息
     * @return Result对象
     */
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<T>();
        result.code = code;
        result.msg = msg;
        return result;
    }

}
