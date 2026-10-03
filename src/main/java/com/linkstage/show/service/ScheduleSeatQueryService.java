package com.linkstage.show.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkstage.common.exception.NotFoundException;
import com.linkstage.seat.repository.SeatHoldState;
import com.linkstage.seat.repository.SeatHoldStore;
import com.linkstage.show.dto.ScheduleSeatsResponse;
import com.linkstage.show.dto.ScheduleSeatsResponse.SeatResponse;
import com.linkstage.show.dto.SeatStatus;
import com.linkstage.show.entity.ScheduleSeat;
import com.linkstage.show.entity.ScheduleSeatStatus;
import com.linkstage.show.repository.ScheduleRepository;
import com.linkstage.show.repository.ScheduleSeatRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleSeatQueryService {

	private final ScheduleRepository scheduleRepository;

	private final ScheduleSeatRepository scheduleSeatRepository;

	private final SeatHoldStore seatHoldStore;

	// 좌석 정보는 MySQL에서, 누가 잡았는지는 Redis에서 읽어 합친다.
	public ScheduleSeatsResponse findSeats(Long scheduleId) {
		if (!scheduleRepository.existsById(scheduleId)) {
			throw new NotFoundException("회차를 찾을 수 없습니다: " + scheduleId);
		}
		List<ScheduleSeat> scheduleSeats = scheduleSeatRepository.findAllWithSeat(scheduleId);
		List<Long> seatIds = scheduleSeats.stream().map(ss -> ss.getSeat().getId()).toList();
		Map<Long, SeatHoldState> holdStates = seatHoldStore.states(scheduleId, seatIds);

		List<SeatResponse> seats = scheduleSeats.stream()
			.map(ss -> SeatResponse.of(ss, status(ss, holdStates.get(ss.getSeat().getId()))))
			.toList();
		return new ScheduleSeatsResponse(scheduleId, seats);
	}

	// Redis에 키가 있으면 그 상태를 쓰고, 없으면 DB 상태를 쓴다.
	// Redis가 재시작돼 키가 사라져도 확정된 좌석은 DB 상태로 RESERVED가 된다.
	private static SeatStatus status(ScheduleSeat scheduleSeat, SeatHoldState holdState) {
		if (holdState == SeatHoldState.RESERVED || scheduleSeat.getStatus() == ScheduleSeatStatus.RESERVED) {
			return SeatStatus.RESERVED;
		}
		if (holdState == SeatHoldState.HELD) {
			return SeatStatus.HELD;
		}
		return SeatStatus.AVAILABLE;
	}

}
