package com.linkstage.queue.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 대기열 ZSET queue:{회차}: member는 회원 번호, score는 줄 선 시각(밀리초)이다.
// 점수가 작을수록 앞에 선다. 점수가 같으면 Redis가 member 글자 순서로 정렬한다.
// 입장 명단 ZSET queue_active:{회차}: member는 회원 번호, score는 입장 만료 시각(밀리초)이다.
@Component
@RequiredArgsConstructor
public class QueueStore {

	private static final RedisScript<Long> ADMIT = RedisScript.of(new ClassPathResource("lua/queue/admit.lua"),
			Long.class);

	private final StringRedisTemplate redis;

	// {회차ID}는 Redis Cluster 해시태그다. 좌석 키와 같은 형식이다.
	public static String key(long scheduleId) {
		return "queue:{" + scheduleId + "}";
	}

	// 입장 명단. 대기열과 같은 해시태그를 써서 admit.lua가 두 키를 함께 다룰 수 있게 한다.
	public static String activeKey(long scheduleId) {
		return "queue_active:{" + scheduleId + "}";
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

	// 배출 한 번: 만료 정리 → 빈자리(정원 − 명단 인원, 하드캡 이하)만큼 줄 앞에서 꺼내 명단에 넣는다.
	// 이번에 입장시킨 인원을 돌려준다.
	public int admit(long scheduleId, long nowMillis, int capacity, int hardCap, long expireAtMillis) {
		Long admitted = redis.execute(ADMIT, List.of(key(scheduleId), activeKey(scheduleId)), String.valueOf(nowMillis),
				String.valueOf(capacity), String.valueOf(hardCap), String.valueOf(expireAtMillis));
		return admitted == null ? 0 : admitted.intValue();
	}

	// 입장 명단에 있고 아직 만료되지 않았는지 본다. 기준은 admit.lua와 같다: 점수(입장 만료 시각) > 지금 시각.
	// 만료됐지만 스케줄러가 아직 지우지 않은 회원은 입장한 것으로 보지 않는다.
	public boolean isAdmitted(long scheduleId, long memberId, long nowMillis) {
		Double expireAt = redis.opsForZSet().score(activeKey(scheduleId), String.valueOf(memberId));
		return expireAt != null && expireAt > nowMillis;
	}

}
