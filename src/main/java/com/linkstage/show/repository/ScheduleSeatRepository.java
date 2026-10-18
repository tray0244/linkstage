package com.linkstage.show.repository;

import java.util.Collection;
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

	// 회차에 실제로 있는 좌석만 돌려준다. 요청한 좌석 수와 다르면 없는 좌석이 섞인 것이다.
	@Query("select ss from ScheduleSeat ss join fetch ss.seat s where ss.schedule.id = :scheduleId and s.id in :seatIds")
	List<ScheduleSeat> findAllBySeatIds(@Param("scheduleId") Long scheduleId, @Param("seatIds") Collection<Long> seatIds);

	// schedule_seat id로 찾되, 좌석 id를 읽을 수 있게 좌석을 함께 가져온다.
	@Query("select ss from ScheduleSeat ss join fetch ss.seat where ss.id in :ids")
	List<ScheduleSeat> findAllWithSeatByIds(@Param("ids") Collection<Long> ids);

	// schedule_seat id로 찾되, 가격을 읽을 수 있게 좌석과 등급을 함께 가져온다.
	@Query("select ss from ScheduleSeat ss join fetch ss.seat s join fetch s.seatGrade where ss.id in :ids")
	List<ScheduleSeat> findAllWithGradeByIds(@Param("ids") Collection<Long> ids);

}
