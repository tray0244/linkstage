package com.linkstage.seat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.linkstage.TestcontainersConfiguration;

// Testcontainers가 띄운 실제 Redis에서 release.lua를 검증한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatReleaseConcurrencyTest {

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
	void 내가_잡은_좌석을_해제하면_다른_요청이_잡을_수_있다() {
		store.hold(scheduleId, List.of(1L, 2L), "mine");

		assertThat(store.release(scheduleId, List.of(1L, 2L), "mine")).isEqualTo(2);

		assertThat(store.hold(scheduleId, List.of(1L, 2L), "other")).isTrue();
	}

	@Test
	void 다른_requestId로_해제하면_아무것도_지워지지_않는다() {
		store.hold(scheduleId, List.of(1L), "mine");

		assertThat(store.release(scheduleId, List.of(1L), "other")).isZero();

		assertThat(redis.opsForValue().get(SeatKeys.seat(scheduleId, 1L))).isEqualTo("HELD:mine");
	}

	@Test
	void 같은_해제를_두_번_하면_두_번째는_0을_돌려준다() {
		store.hold(scheduleId, List.of(1L), "mine");

		assertThat(store.release(scheduleId, List.of(1L), "mine")).isEqualTo(1);
		assertThat(store.release(scheduleId, List.of(1L), "mine")).isZero();
	}

	@Test
	void 잡고_풀기를_반복해도_한_좌석을_잡은_스레드는_항상_1개_이하다() throws InterruptedException {
		int threads = 50;
		int rounds = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicInteger holders = new AtomicInteger();
		AtomicInteger success = new AtomicInteger();
		AtomicBoolean violated = new AtomicBoolean();

		for (int i = 0; i < threads; i++) {
			pool.submit(() -> {
				try {
					start.await();
					for (int r = 0; r < rounds; r++) {
						String requestId = UUID.randomUUID().toString();
						if (store.hold(scheduleId, List.of(1L), requestId)) {
							success.incrementAndGet();
							// 지금 이 좌석을 잡고 있는 스레드 수. 2 이상이면 중복 선점이다.
							if (holders.incrementAndGet() > 1) {
								violated.set(true);
							}
							holders.decrementAndGet();
							store.release(scheduleId, List.of(1L), requestId);
						}
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
		start.countDown();
		assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
		pool.shutdown();

		assertThat(violated).isFalse();
		assertThat(success.get()).isPositive();
		assertThat(redis.hasKey(SeatKeys.seat(scheduleId, 1L))).isFalse();
	}

}
