package com.linkstage.queue.repository;

import org.springframework.stereotype.Component;

import com.linkstage.common.config.QueueProperties;

import lombok.RequiredArgsConstructor;

// 정원 M과 하드캡을 읽는 유일한 곳이다.
// 지금은 application.yml에서 읽고 모든 회차가 같은 값을 쓴다.
// 측정 결과에 따라 Redis queue_control:{회차}로 옮기면 이 클래스만 바꾼다. 그래서 회차 번호를 받는다.
@Component
@RequiredArgsConstructor
public class QueueLimitStore {

	private final QueueProperties queueProperties;

	public int capacity(long scheduleId) {
		return queueProperties.capacity();
	}

	public int hardCap(long scheduleId) {
		return queueProperties.hardCap();
	}

}
