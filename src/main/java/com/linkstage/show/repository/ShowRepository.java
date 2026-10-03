package com.linkstage.show.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkstage.show.entity.Show;
import com.linkstage.show.entity.ShowStatus;

public interface ShowRepository extends JpaRepository<Show, Long> {

	// 공연과 회차를 쿼리 1번으로 가져온다.
	@Query("select distinct s from Show s left join fetch s.schedules where s.status = :status order by s.id")
	List<Show> findAllWithSchedules(@Param("status") ShowStatus status);

}
