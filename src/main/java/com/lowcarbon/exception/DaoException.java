package com.lowcarbon.exception;

/**
 * 数据访问层抛出的运行时异常。
 * <p>
 * DAO 层方法应捕获 {@link java.sql.SQLException} 并包装为 DaoException 向上抛出，
 * 避免业务层直接依赖 JDBC 异常类型。
 *
 * @author lowcarbon-team
 * @since 2026-11-08
 */
public class DaoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造 DAO 异常（无根本原因）。
     *
     * @param message 错误描述
     */
    public DaoException(String message) {
        super(message);
    }

    /**
     * 构造 DAO 异常（带根本原因）。
     *
     * @param message 错误描述
     * @param cause   底层异常（通常是 {@link java.sql.SQLException}）
     */
    public DaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
