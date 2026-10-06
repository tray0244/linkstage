package com.linkstage.common.exception;

import lombok.Getter;

// 예상한 오류는 모두 이 예외로 던진다. GlobalExceptionHandler가 ErrorResponse로 바꾼다.
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getDefaultMessage());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

}
