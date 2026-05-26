package com.xinya.ops.common.domain;

import lombok.*;

/**
 * 统一响应封装 {code, message, data}
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
