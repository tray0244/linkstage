package com.linkstage.common.testsupport.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.linkstage.common.testsupport.service.TestSupportService;

import lombok.RequiredArgsConstructor;

// 테스트 전용 API. local 프로필에서만 켜진다. 다른 프로필에서는 이 주소가 404다.
@RestController
@Profile("local")
@RequiredArgsConstructor
public class TestSupportController {

	private final TestSupportService testSupportService;

	@PostMapping("/api/test/schedules/{scheduleId}/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetSchedule(@PathVariable Long scheduleId) {
		testSupportService.resetSchedule(scheduleId);
	}

}
