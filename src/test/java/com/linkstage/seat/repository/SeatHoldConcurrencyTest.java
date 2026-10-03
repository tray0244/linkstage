package com.linkstage.seat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.linkstage.TestcontainersConfiguration;

// Testcontainers가 띄운 실제 Redis에서 hold.lua를 검증한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatHoldConcurrencyTest {

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
	void 스레드_100개가_같은_좌석을_동시에_잡으면_1건만_성공한다() throws InterruptedException {
		int threads = 100;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicInteger success = new AtomicInteger();
		AtomicInteger fail = new AtomicInteger();

		for (int i = 0; i < threads; i++) {
			pool.submit(() -> {
				ready.countDown();
				try {
					start.await();
					if (store.hold(scheduleId, List.of(1L), UUID.randomUUID().toString())) {
						success.incrementAndGet();
					}
					else {
						fail.incrementAndGet();
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				finally {
					done.countDown();
				}
			});
		}
		ready.await();
		start.countDown();
		assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		pool.shutdown();

		assertThat(success.get()).isEqualTo(1);
		assertThat(fail.get()).isEqualTo(threads - 1);
	}

	@Test
	void 좌석_하나라도_잡혀_있으면_아무것도_잡지_않는다() {
		assertThat(store.hold(scheduleId, List.of(2L), "first")).isTrue();

		assertThat(store.hold(scheduleId, List.of(1L, 2L, 3L), "second")).isFalse();

		assertThat(redis.hasKey(SeatKeys.seat(scheduleId, 1L))).isFalse();
		assertThat(redis.hasKey(SeatKeys.seat(scheduleId, 3L))).isFalse();
		assertThat(redis.opsForValue().get(SeatKeys.seat(scheduleId, 2L))).isEqualTo("HELD:first");
	}

	@Test
	void 같은_requestId로_다시_요청하면_첫_결과를_돌려준다() {
		assertThat(store.hold(scheduleId, List.of(1L), "same")).isTrue();
		assertThat(store.hold(scheduleId, List.of(1L), "same")).isTrue();

		assertThat(store.hold(scheduleId, List.of(1L), "other")).isFalse();
		assertThat(store.hold(scheduleId, List.of(1L), "other")).isFalse();
	}

	@Test
	void 잡은_좌석에는_TTL이_걸린다() {
		store.hold(scheduleId, List.of(1L), "ttl");

		Long ttl = redis.getExpire(SeatKeys.seat(scheduleId, 1L), TimeUnit.SECONDS);
		assertThat(ttl).isBetween(1L, SeatHoldStore.HOLD_TTL.toSeconds());
	}

}
