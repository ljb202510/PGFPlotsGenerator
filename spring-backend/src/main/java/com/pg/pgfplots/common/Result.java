package com.pg.pgfplots.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 全接口统一响应体 {success, data, message}。
 * <p>替代原 Node 端的 {success,data,message} 与 {code,message,data} 两种风格。</p>
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    /** 是否成功 */
    private boolean success;

    /** 业务数据 */
    private T data;

    /** 提示信息 */
    private String message;

    public Result() {
    }

    public Result(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    /** 成功，仅数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(true, data, null);
    }

    /** 成功，带消息与数据 */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(true, data, message);
    }

    /** 成功，仅消息 */
    public static <T> Result<T> okMsg(String message) {
        return new Result<>(true, null, message);
    }

    /** 成功，无数据无消息 */
    public static <T> Result<T> success() {
        return new Result<>(true, null, null);
    }

    /** 失败，仅消息 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(false, null, message);
    }

    /** 失败，带数据 */
    public static <T> Result<T> fail(String message, T data) {
        return new Result<>(false, data, message);
    }
}
