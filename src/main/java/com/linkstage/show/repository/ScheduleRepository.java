package com.linkstage.show.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkstage.show.entity.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}
