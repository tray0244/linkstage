package com.linkstage.show.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkstage.show.entity.ScheduleSeat;

public interface ScheduleSeatRepository extends JpaRepository<ScheduleSeat, Long> {

	// 회차의 좌석을 좌석 정보·등급과 함께 쿼리 1번으로 가져온다.
	@Query("select ss from ScheduleSeat ss join fetch ss.seat s join fetch s.seatGrade"
			+ " where ss.schedule.id = :scheduleId order by s.id")
	List<ScheduleSeat> findAllWithSeat(@Param("scheduleId") Long scheduleId);

}
