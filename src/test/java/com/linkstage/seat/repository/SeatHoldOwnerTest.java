package com.linkstage.seat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.linkstage.TestcontainersConfiguration;

// Testcontainers가 띄운 실제 Redis에서 선점 주인 확인(isHeldBy)을 검증한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatHoldOwnerTest {

	@Autowired
	SeatHoldStore store;

	@Autowired
	StringRedisTemplate redis;

	long scheduleId;

	// 테스트마다 다른 회차를 써서 서로의 키가 겹치지 않게 한다.
	@BeforeEach
	void setUp() {
		scheduleId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
	}

	@AfterEach
	void cleanUp() {
		redis.delete(redis.keys("schedule_seat:{" + scheduleId + "}:*"));
		redis.delete(redis.keys("hold_request:{" + scheduleId + "}:*"));
	}

	@Test
	void 좌석이_모두_내_선점이면_true다() {
		store.hold(scheduleId, List.of(1L, 2L), "mine");

		assertThat(store.isHeldBy(scheduleId, List.of(1L, 2L), "mine")).isTrue();
	}

	@Test
	void 다른_requestId가_잡은_좌석이면_false다() {
		store.hold(scheduleId, List.of(1L), "other");

		assertThat(store.isHeldBy(scheduleId, List.of(1L), "mine")).isFalse();
	}

	@Test
	void 좌석_키가_하나라도_없으면_false다() {
		store.hold(scheduleId, List.of(1L, 2L), "mine");
		redis.delete(SeatKeys.seat(scheduleId, 2L));

		assertThat(store.isHeldBy(scheduleId, List.of(1L, 2L), "mine")).isFalse();
	}

	@Test
	void 좌석_목록이_비어_있으면_false다() {
		assertThat(store.isHeldBy(scheduleId, List.of(), "mine")).isFalse();
	}

	@Test
	void 확정된_좌석이면_false다() {
		store.hold(scheduleId, List.of(1L), "mine");
		store.confirm(scheduleId, List.of(1L), "mine");

		assertThat(store.isHeldBy(scheduleId, List.of(1L), "mine")).isFalse();
	}

}
