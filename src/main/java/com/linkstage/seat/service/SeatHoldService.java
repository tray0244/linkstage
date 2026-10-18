package com.linkstage.seat.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.linkstage.common.config.QueueProperties;
import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.queue.repository.QueueStore;
import com.linkstage.seat.dto.HoldRequest;
import com.linkstage.seat.dto.HoldResponse;
import com.linkstage.seat.entity.HoldGroup;
import com.linkstage.seat.entity.HoldGroupStatus;
import com.linkstage.seat.repository.HoldGroupRepository;
import com.linkstage.seat.repository.SeatHoldStore;
import com.linkstage.show.entity.Schedule;
import com.linkstage.show.entity.ScheduleSeat;
import com.linkstage.show.repository.ScheduleRepository;
import com.linkstage.show.repository.ScheduleSeatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 좌석 잡기·풀기. Redis Lua로 먼저 판정하고, 성공했을 때만 DB에 기록한다.
// 이 클래스에는 트랜잭션을 걸지 않는다. DB 저장은 HoldRecorder가 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldService {

	private final ScheduleRepository scheduleRepository;

	private final ScheduleSeatRepository scheduleSeatRepository;

	private final HoldGroupRepository holdGroupRepository;

	private final SeatHoldStore seatHoldStore;

	private final QueueStore queueStore;

	private final QueueProperties queueProperties;

	private final HoldRecorder holdRecorder;

	public HoldResponse hold(Long memberId, Long scheduleId, HoldRequest request) {
		String requestId = request.requestId();
		List<Long> seatIds = request.seatIds().stream().sorted().toList();
		if (new HashSet<>(seatIds).size() != seatIds.size()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "같은 좌석이 두 번 들어 있습니다.");
		}
		Schedule schedule = scheduleRepository.findWithShow(scheduleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다: " + scheduleId));
		if (seatIds.size() > schedule.getShow().getMaxTicketsPerOrder()) {
			throw new BusinessException(ErrorCode.TOO_MANY_SEATS);
		}

		// 같은 requestId를 이미 처리했으면 그 결과를 그대로 돌려준다 (멱등).
		// 아래 판매 상태 검사보다 먼저 한다. 처리한 뒤에 판매가 끝났어도 처음 결과를 준다.
		Optional<HoldGroup> existing = holdGroupRepository.findWithSeatHolds(requestId);
		if (existing.isPresent()) {
			return toResponse(memberId, existing.get());
		}

		// 대기열 스위치와 관계없이 언제나 검사한다. 위에서 읽은 회차를 쓴다.
		if (!schedule.isOnSale()) {
			throw new BusinessException(ErrorCode.NOT_ON_SALE, "판매 중인 회차가 아닙니다: " + scheduleId);
		}

		// 대기열이 켜져 있으면 입장 명단에 있고 만료되지 않은 회원만 받는다. 입장 토큰 없이 명단을 직접 본다.
		// 이 확인과 hold.lua 사이의 몇 밀리초 틈은 받아들인다.
		if (queueProperties.enabled() && !queueStore.isAdmitted(scheduleId, memberId, System.currentTimeMillis())) {
			throw new BusinessException(ErrorCode.ENTRY_REQUIRED);
		}

		List<ScheduleSeat> scheduleSeats = scheduleSeatRepository.findAllBySeatIds(scheduleId, seatIds);
		if (scheduleSeats.size() != seatIds.size()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "이 회차에 없는 좌석이 있습니다.");
		}

		if (!seatHoldStore.hold(scheduleId, seatIds, requestId)) {
			throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE);
		}

		LocalDateTime expiresAt = LocalDateTime.now().plus(SeatHoldStore.HOLD_TTL);
		List<Long> scheduleSeatIds = scheduleSeats.stream().map(ScheduleSeat::getId).toList();
		try {
			holdRecorder.save(HoldGroup.hold(requestId, memberId, scheduleId, scheduleSeatIds, expiresAt));
		}
		catch (DataIntegrityViolationException e) {
			// 같은 requestId가 동시에 두 번 와서 다른 쪽이 먼저 저장했다.
			// 좌석은 먼저 저장한 요청의 것이므로 풀지 않고, 그 결과를 준다.
			Optional<HoldGroup> saved = holdGroupRepository.findWithSeatHolds(requestId);
			if (saved.isPresent()) {
				return toResponse(memberId, saved.get());
			}
			rollback(scheduleId, seatIds, requestId);
			throw e;
		}
		catch (RuntimeException e) {
			rollback(scheduleId, seatIds, requestId);
			throw e;
		}
		extendAdmission(scheduleId, memberId);
		return new HoldResponse(requestId, seatIds, expiresAt);
	}

	// 좌석을 잡으면 입장 유지 시간을 선점 시간(10분)만큼 늘린다. 결제하는 동안 명단에서 빠지지 않게 하려는 것이다.
	// DB 저장까지 성공한 뒤에만 부른다. 늘리기가 실패해도 좌석은 이미 잡혔으므로 로그만 남긴다.
	private void extendAdmission(Long scheduleId, Long memberId) {
		if (!queueProperties.enabled()) {
			return;
		}
		try {
			queueStore.extendAdmission(scheduleId, memberId, System.currentTimeMillis() + SeatHoldStore.HOLD_TTL.toMillis());
		}
		catch (RuntimeException e) {
			log.warn("입장 유지 시간 늘리기 실패: 회차 {}, 회원 {}", scheduleId, memberId, e);
		}
	}

	public void release(Long memberId, String requestId) {
		HoldGroup group = findMine(memberId, requestId);
		// 이미 풀렸거나 만료로 적힌 선점은 그대로 둔다. EXPIRED를 RELEASED로 덮지 않는다.
		if (group.getStatus() == HoldGroupStatus.RELEASED || group.getStatus() == HoldGroupStatus.EXPIRED) {
			return;
		}
		if (group.getStatus() == HoldGroupStatus.CONFIRMED) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "결제가 끝난 선점은 풀 수 없습니다.");
		}
		// release.lua가 지운 좌석 수로 판단한다. 0개면 Redis에서 이미 만료된 선점이다.
		// 확인(MGET)과 해제를 따로 하면 그 사이에 키가 만료될 수 있어서, 해제 결과를 그대로 쓴다.
		int released = seatHoldStore.release(group.getScheduleId(), seatIds(group), requestId);
		if (released == 0) {
			holdRecorder.markExpired(group.getId());
			return;
		}
		holdRecorder.markReleased(group.getId());
	}

	// 결제할 수 있는 내 선점을 돌려준다. 결제 준비가 부른다.
	// 선점이 살아 있는지는 Redis로 판단한다. 좌석 키가 모두 "HELD:이 requestId"여야 한다.
	// Redis에서 이미 만료됐거나 다른 요청이 잡았으면 EXPIRED로 적고 409를 준다.
	public HoldGroup getLiveHold(Long memberId, String requestId) {
		HoldGroup group = findMine(memberId, requestId);
		switch (group.getStatus()) {
			case CONFIRMED -> throw new BusinessException(ErrorCode.ALREADY_PAID);
			case RELEASED -> throw new BusinessException(ErrorCode.HOLD_EXPIRED, "좌석이 풀렸습니다.");
			case EXPIRED -> throw new BusinessException(ErrorCode.HOLD_EXPIRED);
			case HOLDING -> {
			}
		}
		if (!seatHoldStore.isHeldBy(group.getScheduleId(), seatIds(group), requestId)) {
			holdRecorder.markExpired(group.getId());
			throw new BusinessException(ErrorCode.HOLD_EXPIRED);
		}
		return group;
	}

	// 남의 선점이면 없는 것과 같게 404를 준다. 있는지 없는지도 알려 주지 않는다.
	private HoldGroup findMine(Long memberId, String requestId) {
		return holdGroupRepository.findWithSeatHolds(requestId)
			.filter(g -> g.getMemberId().equals(memberId))
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "선점을 찾을 수 없습니다."));
	}

	// DB 저장에 실패했을 때 Redis 선점을 되돌린다.
	// 멱등키를 먼저 지운다. 좌석을 먼저 풀면, 두 명령 사이에 온 같은 requestId 재시도가
	// 남은 멱등키 'OK'를 받아 좌석 없이 성공 처리될 수 있다.
	// 멱등키를 먼저 지우면 그 재시도는 아직 잡혀 있는 좌석을 보고 실패(409)한다.
	private void rollback(Long scheduleId, List<Long> seatIds, String requestId) {
		seatHoldStore.deleteHoldRequest(scheduleId, requestId);
		seatHoldStore.release(scheduleId, seatIds, requestId);
	}

	private HoldResponse toResponse(Long memberId, HoldGroup group) {
		if (!group.getMemberId().equals(memberId)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 사용된 requestId입니다.");
		}
		return new HoldResponse(group.getRequestId(), seatIds(group), group.getExpiresAt());
	}

	// seat_hold에는 schedule_seat id만 있다. Redis 키와 응답에는 좌석 id가 필요하다. 작은 번호부터 준다.
	private List<Long> seatIds(HoldGroup group) {
		return scheduleSeatRepository.findAllWithSeatByIds(group.scheduleSeatIds())
			.stream()
			.map(ss -> ss.getSeat().getId())
			.sorted()
			.toList();
	}

}
