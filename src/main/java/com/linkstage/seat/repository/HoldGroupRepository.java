package com.linkstage.seat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkstage.seat.entity.HoldGroup;

public interface HoldGroupRepository extends JpaRepository<HoldGroup, Long> {

	// 선점 기록과 좌석 기록을 쿼리 1번으로 가져온다.
	@Query("select g from HoldGroup g join fetch g.seatHolds where g.requestId = :requestId")
	Optional<HoldGroup> findWithSeatHolds(@Param("requestId") String requestId);

}
