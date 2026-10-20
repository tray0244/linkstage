package com.linkstage.seat.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.linkstage.seat.entity.HoldGroup;
import com.linkstage.seat.entity.HoldGroupStatus;
import com.linkstage.seat.entity.SeatHoldStatus;
import com.linkstage.seat.repository.HoldGroupRepository;

import lombok.RequiredArgsConstructor;

// 선점 기록의 DB 저장만 맡는다. SeatHoldService와 다른 클래스에 둔 이유:
// 같은 클래스 안에서 @Transactional 메서드를 부르면 트랜잭션이 걸리지 않는다.
// 그리고 Redis 호출은 트랜잭션 밖에 둔다.
@Component
@RequiredArgsConstructor
class HoldRecorder {

	private final HoldGroupRepository holdGroupRepository;

	// saveAndFlush: INSERT를 바로 실행한다. 중복 키 같은 오류가 이 메서드 안에서 나게 하려는 것이다.
	@Transactional
	public HoldGroup save(HoldGroup group) {
		return holdGroupRepository.saveAndFlush(group);
	}

	// 아래 두 메서드는 HOLDING일 때만 바꾸고, 바꿨으면 true를 준다.
	// false면 그 사이에 다른 요청이 상태를 먼저 바꾼 것이다. 부른 쪽이 다시 읽어서 답한다.
	@Transactional
	public boolean markReleased(Long holdGroupId) {
		return mark(holdGroupId, HoldGroupStatus.RELEASED, SeatHoldStatus.RELEASED);
	}

	@Transactional
	public boolean markExpired(Long holdGroupId) {
		return mark(holdGroupId, HoldGroupStatus.EXPIRED, SeatHoldStatus.EXPIRED);
	}

	private boolean mark(Long holdGroupId, HoldGroupStatus groupStatus, SeatHoldStatus seatStatus) {
		if (holdGroupRepository.updateStatusIfHolding(holdGroupId, groupStatus) == 0) {
			return false;
		}
		holdGroupRepository.updateSeatStatusIfHeld(holdGroupId, seatStatus);
		return true;
	}

}
