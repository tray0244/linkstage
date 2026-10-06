package com.linkstage.show.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.linkstage.show.dto.ScheduleSeatsResponse;
import com.linkstage.show.service.ScheduleSeatQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "회차 좌석", description = "회차의 좌석 정보와 좌석별 상태")
@RestController
@RequiredArgsConstructor
public class ScheduleSeatController {

	private final ScheduleSeatQueryService scheduleSeatQueryService;

	@Operation(summary = "회차 좌석 현황", description = "회차의 좌석과 상태(AVAILABLE / HELD / RESERVED)를 준다. 성공하면 200, 없는 회차는 404 NOT_FOUND.")
	@GetMapping("/api/schedules/{scheduleId}/seats")
	public ScheduleSeatsResponse seats(@PathVariable Long scheduleId) {
		return scheduleSeatQueryService.findSeats(scheduleId);
	}

}
