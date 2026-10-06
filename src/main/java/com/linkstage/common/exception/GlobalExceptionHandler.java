package com.linkstage.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> business(BusinessException e) {
		return toResponse(e.getErrorCode(), e.getMessage());
	}

	// @Valid 검사에 걸린 요청, JSON을 읽을 수 없는 요청
	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
	public ResponseEntity<ErrorResponse> invalid(Exception e) {
		return toResponse(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.getDefaultMessage());
	}

	private static ResponseEntity<ErrorResponse> toResponse(ErrorCode code, String message) {
		return ResponseEntity.status(code.getStatus()).body(new ErrorResponse(code.name(), message));
	}

}
