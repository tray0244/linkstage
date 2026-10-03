package com.linkstage.show.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.linkstage.show.entity.Schedule;
import com.linkstage.show.entity.ScheduleStatus;
import com.linkstage.show.entity.Show;

public record ShowResponse(Long id, String title, String venueName, LocalDateTime openAt, int maxTicketsPerOrder,
		List<ScheduleResponse> schedules) {

	public record ScheduleResponse(Long id, LocalDateTime startsAt, ScheduleStatus status) {

		public static ScheduleResponse from(Schedule schedule) {
			return new ScheduleResponse(schedule.getId(), schedule.getStartsAt(), schedule.getStatus());
		}

	}

	public static ShowResponse from(Show show) {
		return new ShowResponse(show.getId(), show.getTitle(), show.getVenueName(), show.getOpenAt(),
				show.getMaxTicketsPerOrder(), show.getSchedules().stream().map(ScheduleResponse::from).toList());
	}

}
