package com.xinya.common.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应封装（保持 {code, message, data} 字段名以兼容现有前端/Android）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {

    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok() {
        return R.<T>builder().code(200).message("success").data(null).build();
    }

    public static <T> R<T> ok(T data) {
        return R.<T>builder().code(200).message("success").data(data).build();
    }

    public static <T> R<T> ok(String message, T data) {
        return R.<T>builder().code(200).message(message).data(data).build();
    }

    public static <T> R<T> fail(int code, String message) {
        return R.<T>builder().code(code).message(message).data(null).build();
    }

    public static <T> R<T> fail(String message) {
        return fail(500, message);
    }

    public boolean isOk() {
        return this.code == 200;
    }
}
