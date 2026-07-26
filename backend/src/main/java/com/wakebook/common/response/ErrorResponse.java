package com.wakebook.common.response;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        Object data
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, null);
    }
}
