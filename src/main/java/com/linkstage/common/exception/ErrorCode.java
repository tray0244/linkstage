package com.linkstage.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 오류 코드. docs/API명세서.md "오류 코드" 표와 같게 유지한다.
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 잘못되었습니다."),
	TOO_MANY_SEATS(HttpStatus.BAD_REQUEST, "한 번에 잡을 수 있는 좌석 수를 넘었습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "회원 번호가 없습니다."),
	ENTRY_REQUIRED(HttpStatus.FORBIDDEN, "대기열에서 입장해주세요."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없습니다."),
	SEAT_UNAVAILABLE(HttpStatus.CONFLICT, "이미 잡힌 좌석이 있습니다."),
	HOLD_EXPIRED(HttpStatus.CONFLICT, "좌석이 만료되었습니다."),
	ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제가 완료되었습니다."),
	NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 회차가 아닙니다."),
	NOT_CANCELABLE(HttpStatus.CONFLICT, "취소할 수 없는 예매입니다.");

	private final HttpStatus status;

	private final String defaultMessage;

}
