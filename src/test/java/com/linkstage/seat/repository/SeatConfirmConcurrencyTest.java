package com.linkstage.seat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.linkstage.TestcontainersConfiguration;

// Testcontainers가 띄운 실제 Redis에서 confirm.lua를 검증한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatConfirmConcurrencyTest {

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

	@Test
	void 확정하면_RESERVED가_되고_TTL이_없어진다() {
		store.hold(scheduleId, List.of(1L, 2L), "mine");

		assertThat(store.confirm(scheduleId, List.of(1L, 2L), "mine")).isTrue();

		String key = SeatKeys.seat(scheduleId, 1L);
		assertThat(redis.opsForValue().get(key)).isEqualTo("RESERVED:mine");
		// TTL이 없는 키는 -1을 돌려준다.
		assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isEqualTo(-1L);
		assertThat(store.hold(scheduleId, List.of(1L), "other")).isFalse();
	}

	@Test
	void 하나라도_내_선점이_아니면_아무것도_확정하지_않는다() {
		store.hold(scheduleId, List.of(1L, 2L), "mine");
		// 좌석 2가 만료된 뒤 다른 사람이 잡은 상황을 만든다.
		redis.delete(SeatKeys.seat(scheduleId, 2L));
		store.hold(scheduleId, List.of(2L), "other");

		assertThat(store.confirm(scheduleId, List.of(1L, 2L), "mine")).isFalse();

		assertThat(redis.opsForValue().get(SeatKeys.seat(scheduleId, 1L))).isEqualTo("HELD:mine");
		assertThat(redis.opsForValue().get(SeatKeys.seat(scheduleId, 2L))).isEqualTo("HELD:other");
	}

	@Test
	void 만료된_좌석은_확정되지_않는다() {
		store.hold(scheduleId, List.of(1L), "mine");
		redis.delete(SeatKeys.seat(scheduleId, 1L));

		assertThat(store.confirm(scheduleId, List.of(1L), "mine")).isFalse();
		assertThat(redis.hasKey(SeatKeys.seat(scheduleId, 1L))).isFalse();
	}

	@Test
	void 같은_확정을_두_번_하면_둘_다_성공한다() {
		store.hold(scheduleId, List.of(1L), "mine");

		assertThat(store.confirm(scheduleId, List.of(1L), "mine")).isTrue();
		assertThat(store.confirm(scheduleId, List.of(1L), "mine")).isTrue();
	}

	@Test
	void 확정된_좌석은_해제로_풀리지_않는다() {
		store.hold(scheduleId, List.of(1L), "mine");
		store.confirm(scheduleId, List.of(1L), "mine");

		assertThat(store.release(scheduleId, List.of(1L), "mine")).isZero();
		assertThat(redis.opsForValue().get(SeatKeys.seat(scheduleId, 1L))).isEqualTo("RESERVED:mine");
	}

	// 결제 승인(확정)과 사용자의 취소(해제)가 같은 순간에 오는 상황.
	@Test
	void 확정과_해제를_동시에_보내면_둘_중_하나만_성공한다() throws Exception {
		int seats = 200;
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (long seatId = 1; seatId <= seats; seatId++) {
				List<Long> seat = List.of(seatId);
				String requestId = "req-" + seatId;
				store.hold(scheduleId, seat, requestId);

				CyclicBarrier barrier = new CyclicBarrier(2);
				Future<Boolean> confirmed = pool.submit(() -> {
					barrier.await();
					return store.confirm(scheduleId, seat, requestId);
				});
				Future<Integer> released = pool.submit(() -> {
					barrier.await();
					return store.release(scheduleId, seat, requestId);
				});

				boolean confirmOk = confirmed.get(10, TimeUnit.SECONDS);
				boolean releaseOk = released.get(10, TimeUnit.SECONDS) == 1;
				String value = redis.opsForValue().get(SeatKeys.seat(scheduleId, seatId));

				// 둘 다 성공하거나 둘 다 실패하면 안 된다.
				assertThat(confirmOk).isNotEqualTo(releaseOk);
				// Redis의 최종 값이 성공한 쪽과 맞아야 한다.
				assertThat(value).isEqualTo(confirmOk ? "RESERVED:" + requestId : null);
			}
		}
		finally {
			pool.shutdown();
		}
	}

}
