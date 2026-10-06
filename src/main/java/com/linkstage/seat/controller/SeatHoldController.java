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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SeatHoldController {

	private final SeatHoldService seatHoldService;

	// 입장 토큰(X-Entry-Token) 검사는 ④ 대기열에서 붙인다.
	@PostMapping("/api/schedules/{scheduleId}/holds")
	@ResponseStatus(HttpStatus.CREATED)
	public HoldResponse hold(@PathVariable Long scheduleId, @LoginMember Long memberId,
			@Valid @RequestBody HoldRequest request) {
		return seatHoldService.hold(memberId, scheduleId, request);
	}

	@DeleteMapping("/api/holds/{requestId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void release(@PathVariable String requestId, @LoginMember Long memberId) {
		seatHoldService.release(memberId, requestId);
	}

}
