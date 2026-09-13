package com.pg.pgfplots.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 HTTP 状态码与提示消息，由 {@link GlobalExceptionHandler} 统一转换为 {@link Result}。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 对应的 HTTP 状态码 */
    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** 400 参数/校验错误 */
    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    /** 404 资源不存在或无权访问 */
    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    /** 403 权限不足 */
    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }

    /** 500 服务器错误 */
    public static BusinessException serverError(String message) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
