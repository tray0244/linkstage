package com.linkstage.show.dto;

import java.util.List;

import com.linkstage.show.entity.ScheduleSeat;
import com.linkstage.show.entity.Seat;

public record ScheduleSeatsResponse(Long scheduleId, List<SeatResponse> seats) {

	public record SeatResponse(Long seatId, String section, String rowLabel, int seatNo, String grade, int price,
			SeatStatus status) {

		public static SeatResponse of(ScheduleSeat scheduleSeat, SeatStatus status) {
			Seat seat = scheduleSeat.getSeat();
			return new SeatResponse(seat.getId(), seat.getSection(), seat.getRowLabel(), seat.getSeatNo(),
					seat.getSeatGrade().getName(), seat.getSeatGrade().getPrice(), status);
		}

	}

}
