package com.linkstage.queue.repository;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

// 테스트 도우미: 회원을 입장 명단(queue_active:{회차})에 바로 넣는다.
// 대기열이 켜져 있으면 좌석 잡기는 명단에 있는 회원만 받으므로, 좌석을 잡는 테스트는 먼저 이것을 부른다.
public final class AdmissionTestHelper {

	private static final Duration ADMISSION_TTL = Duration.ofMinutes(5);

	private AdmissionTestHelper() {
	}

	// 입장 만료 시각 = 지금 + 5분
	public static void admit(StringRedisTemplate redis, long scheduleId, long... memberIds) {
		long expireAt = System.currentTimeMillis() + ADMISSION_TTL.toMillis();
		for (long memberId : memberIds) {
			redis.opsForZSet().add(QueueStore.activeKey(scheduleId), String.valueOf(memberId), expireAt);
		}
	}

}
