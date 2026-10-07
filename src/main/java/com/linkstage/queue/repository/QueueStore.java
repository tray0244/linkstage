package com.linkstage.queue.repository;

import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 대기열 ZSET. member는 회원 번호, score는 줄 선 시각(밀리초)이다.
// 점수가 작을수록 앞에 선다. 점수가 같으면 Redis가 member 글자 순서로 정렬한다.
@Component
@RequiredArgsConstructor
public class QueueStore {

	private final StringRedisTemplate redis;

	// {회차ID}는 Redis Cluster 해시태그다. 좌석 키와 같은 형식이다.
	public static String key(long scheduleId) {
		return "queue:{" + scheduleId + "}";
	}

	// 줄을 세우고 순번(맨 앞이 1)을 돌려준다. ZADD는 이미 있는 회원의 점수를 새 시각으로 덮어쓰므로,
	// 다시 줄 서면 맨 뒤로 간다. ZADD와 ZRANK는 명령 두 번이라 사이에 다른 회원이 끼면
	// 순번이 1~2 달라질 수 있다. 안내용 숫자라 받아들인다.
	public long join(long scheduleId, long memberId, long nowMillis) {
		String member = String.valueOf(memberId);
		redis.opsForZSet().add(key(scheduleId), member, nowMillis);
		Long rank = redis.opsForZSet().rank(key(scheduleId), member);
		if (rank == null) {
			// 지금은 줄에서 빠지는 경로가 없다. ④-2에서 배출이 생기면 이 경우를 다시 정한다.
			throw new IllegalStateException("줄 선 직후 순번이 없습니다: " + key(scheduleId) + " " + member);
		}
		return rank + 1;
	}

	// 순번(맨 앞이 1). 줄에 없으면 빈 값이다.
	public Optional<Long> position(long scheduleId, long memberId) {
		Long rank = redis.opsForZSet().rank(key(scheduleId), String.valueOf(memberId));
		return Optional.ofNullable(rank).map(r -> r + 1);
	}

}
