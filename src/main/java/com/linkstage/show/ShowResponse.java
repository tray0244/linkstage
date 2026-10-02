package com.linkstage.show;

import java.time.LocalDateTime;
import java.util.List;

public record ShowResponse(Long id, String title, String venueName, LocalDateTime openAt, int maxTicketsPerOrder,
		List<ScheduleResponse> schedules) {

	public record ScheduleResponse(Long id, LocalDateTime startsAt, ScheduleStatus status) {

		static ScheduleResponse from(Schedule schedule) {
			return new ScheduleResponse(schedule.getId(), schedule.getStartsAt(), schedule.getStatus());
		}

	}

	static ShowResponse from(Show show) {
		return new ShowResponse(show.getId(), show.getTitle(), show.getVenueName(), show.getOpenAt(),
				show.getMaxTicketsPerOrder(), show.getSchedules().stream().map(ScheduleResponse::from).toList());
	}

}
