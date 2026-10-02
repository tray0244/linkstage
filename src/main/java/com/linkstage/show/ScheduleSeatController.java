package com.linkstage.show;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScheduleSeatController {

	private final ScheduleSeatQueryService scheduleSeatQueryService;

	@GetMapping("/api/schedules/{scheduleId}/seats")
	public ScheduleSeatsResponse seats(@PathVariable Long scheduleId) {
		return scheduleSeatQueryService.findSeats(scheduleId);
	}

}
