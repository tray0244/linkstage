package com.linkstage.show.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkstage.show.entity.Schedule;
import com.linkstage.show.entity.ScheduleStatus;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

	// 회차와 공연을 쿼리 1번으로 가져온다 (공연의 1회 최대 매수를 보려고).
	@Query("select sc from Schedule sc join fetch sc.show where sc.id = :id")
	Optional<Schedule> findWithShow(@Param("id") Long id);

	// 상태가 같은 회차의 id만 읽는다.
	@Query("select sc.id from Schedule sc where sc.status = :status")
	List<Long> findIdsByStatus(@Param("status") ScheduleStatus status);

}
