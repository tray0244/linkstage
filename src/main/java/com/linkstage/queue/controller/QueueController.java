package com.linkstage.queue.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkstage.common.auth.LoginMember;
import com.linkstage.queue.dto.QueueResponse;
import com.linkstage.queue.service.QueueService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "대기열", description = "회차별 대기열에 줄 서고 내 순번을 확인한다. X-Member-Id 헤더가 필요하다")
@RestController
@RequiredArgsConstructor
public class QueueController {

	private final QueueService queueService;

	@Operation(summary = "대기열 줄 서기", description = "줄 서고 내 순번을 준다. 이미 줄 서 있거나 입장한 회원이 다시 줄 서면 입장 명단에서 빠지고 줄 맨 뒤로 간다. 성공하면 200, 없는 회차는 404 NOT_FOUND, 판매 중이 아닌 회차는 409 NOT_ON_SALE. 대기열이 꺼져 있으면 같은 검사 뒤 바로 ADMITTED.")
	@PostMapping("/api/schedules/{scheduleId}/queue")
	public QueueResponse join(@PathVariable Long scheduleId, @LoginMember Long memberId) {
		return queueService.join(memberId, scheduleId);
	}

	@Operation(summary = "내 순번 확인", description = "몇 초마다 불러서 내 상태를 확인한다. 입장했으면 200 ADMITTED, 줄에 있으면 200 WAITING과 순번(맨 앞이 1). 입장 명단과 줄에 모두 없거나 대기열이 꺼져 있으면 404 NOT_FOUND.")
	@GetMapping("/api/schedules/{scheduleId}/queue/me")
	public QueueResponse myPosition(@PathVariable Long scheduleId, @LoginMember Long memberId) {
		return queueService.myPosition(memberId, scheduleId);
	}

}
