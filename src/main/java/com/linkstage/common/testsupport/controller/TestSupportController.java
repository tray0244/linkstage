package com.linkstage.common.testsupport.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.linkstage.common.testsupport.service.TestSupportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 테스트 전용 API. local 프로필에서만 켜진다. 다른 프로필에서는 이 주소가 404다.
@Tag(name = "테스트 전용", description = "local 프로필에서만 켜지는 API")
@RestController
@Profile("local")
@RequiredArgsConstructor
public class TestSupportController {

	private final TestSupportService testSupportService;

	@Operation(summary = "회차 좌석 초기화", description = "k6를 다시 돌리기 전에 회차의 선점·결제 기록과 Redis 좌석·대기열 키를 지우고 좌석을 AVAILABLE로 되돌린다. 성공하면 204, 없는 회차는 404 NOT_FOUND.")
	@PostMapping("/api/test/schedules/{scheduleId}/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetSchedule(@PathVariable Long scheduleId) {
		testSupportService.resetSchedule(scheduleId);
	}

}
