package com.lowcarbon.exception;

/**
 * 业务逻辑层抛出的运行时异常。
 * <p>
 * Service 层捕获 DAO 层 {@link DaoException} 或其他异常后，
 * 应包装为 BusinessException 向上抛出，携带对用户友好的错误信息。
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造业务异常（无根本原因）。
     *
     * @param message 对用户友好的错误描述
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * 构造业务异常（带根本原因）。
     *
     * @param message 对用户友好的错误描述
     * @param cause   底层异常（如 {@link java.sql.SQLException}、{@link DaoException}）
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
