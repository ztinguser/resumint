package com.resumint.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class) ResponseEntity<ApiError> api(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(),exception.getMessage(),exception.details(),Instant.now()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED","请求校验失败",Map.of("fieldErrors",exception.getBindingResult().getFieldErrors().stream().map(e->Map.of("field",e.getField(),"message",String.valueOf(e.getDefaultMessage()))).toList()),Instant.now()));
    }
    public record ApiError(String code,String message,Map<String,Object> details,Instant timestamp) { }
}
