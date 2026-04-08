package com.jhun.backend.common.response;

/**
 * 统一响应体。
 * <p>
 * 当前基线阶段先提供最小三段式结构，保证控制层与异常处理层可以使用一致的 JSON 包装格式，
 * 后续扩展分页、追踪号等字段时仍以该对象为唯一出口。
 *
 * @param <T> 业务数据载荷类型
 */
public class Result<T> {

    /** 响应码，0 表示成功，1 表示通用失败。 */
    private final int code;

    /** 响应消息，优先承载面向调用方的可读错误原因。 */
    private final String message;

    /** 业务返回数据，失败场景允许为空。 */
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造成功响应。
     *
     * @param data 成功时返回的业务数据
     * @return 统一成功响应对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    /**
     * 构造失败响应。
     *
     * @param message 失败原因
     * @return 统一失败响应对象
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(1, message, null);
    }

    /**
     * 构造携带失败数据的响应。
     * <p>
     * 新增该重载是为了兼容“HTTP 失败但仍需把结构化阻塞原因返回给前端”的场景，
     * 例如多设备预约失败时返回 {@code blockingDevices[]}，同时不打破既有成功/失败三段式结构。
     *
     * @param message 失败原因
     * @param data 失败时仍需返回的结构化数据
     * @return 统一失败响应对象
     */
    public static <T> Result<T> error(String message, T data) {
        return new Result<>(1, message, data);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
