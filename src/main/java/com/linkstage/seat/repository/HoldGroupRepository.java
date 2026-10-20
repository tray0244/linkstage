package com.linkstage.seat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkstage.seat.entity.HoldGroup;
import com.linkstage.seat.entity.HoldGroupStatus;
import com.linkstage.seat.entity.SeatHoldStatus;

public interface HoldGroupRepository extends JpaRepository<HoldGroup, Long> {

	// 선점 기록과 좌석 기록을 쿼리 1번으로 가져온다.
	@Query("select g from HoldGroup g join fetch g.seatHolds where g.requestId = :requestId")
	Optional<HoldGroup> findWithSeatHolds(@Param("requestId") String requestId);

	// HOLDING일 때만 바꾼다. 읽고 나서 쓰면 그 사이에 다른 요청이 바꾼 상태를 덮기 때문이다.
	// 이 UPDATE가 행을 잠그므로, 동시에 온 다른 요청은 기다렸다가 0행을 받는다.
	@Modifying
	@Query("update HoldGroup g set g.status = :to where g.id = :id"
			+ " and g.status = com.linkstage.seat.entity.HoldGroupStatus.HOLDING")
	int updateStatusIfHolding(@Param("id") Long id, @Param("to") HoldGroupStatus to);

	// updateStatusIfHolding이 1행을 바꿨을 때만 부른다.
	@Modifying
	@Query("update SeatHold s set s.status = :to where s.holdGroup.id = :holdGroupId"
			+ " and s.status = com.linkstage.seat.entity.SeatHoldStatus.HELD")
	int updateSeatStatusIfHeld(@Param("holdGroupId") Long holdGroupId, @Param("to") SeatHoldStatus to);

}
