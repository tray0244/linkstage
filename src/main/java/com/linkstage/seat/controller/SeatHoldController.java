package com.linkstage.seat.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.linkstage.common.auth.LoginMember;
import com.linkstage.seat.dto.HoldRequest;
import com.linkstage.seat.dto.HoldResponse;
import com.linkstage.seat.service.SeatHoldService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "좌석 선점", description = "좌석을 10분 동안 잡고 푼다. X-Member-Id 헤더가 필요하다")
@RestController
@RequiredArgsConstructor
public class SeatHoldController {

	private final SeatHoldService seatHoldService;

	// 입장 토큰(X-Entry-Token) 검사는 ④ 대기열에서 붙인다.
	@Operation(summary = "좌석 잡기", description = "좌석을 전부 잡거나 하나도 잡지 않는다. 같은 requestId로 다시 보내면 첫 결과를 준다. 성공하면 201, 이미 잡힌 좌석이 있으면 409 SEAT_UNAVAILABLE, 판매 중이 아닌 회차는 409 NOT_ON_SALE, 매수 초과는 400 TOO_MANY_SEATS.")
	@PostMapping("/api/schedules/{scheduleId}/holds")
	@ResponseStatus(HttpStatus.CREATED)
	public HoldResponse hold(@PathVariable Long scheduleId, @LoginMember Long memberId,
			@Valid @RequestBody HoldRequest request) {
		return seatHoldService.hold(memberId, scheduleId, request);
	}

	@Operation(summary = "잡은 좌석 풀기", description = "내가 잡은 좌석을 바로 푼다. 성공하면 204이고, 이미 풀었어도 204. 남의 선점이거나 없으면 404 NOT_FOUND.")
	@DeleteMapping("/api/holds/{requestId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void release(@PathVariable String requestId, @LoginMember Long memberId) {
		seatHoldService.release(memberId, requestId);
	}

}
