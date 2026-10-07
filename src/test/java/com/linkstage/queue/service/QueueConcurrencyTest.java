package com.linkstage.queue.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.linkstage.TestcontainersConfiguration;

// Testcontainers가 띄운 실제 Redis에서, 여러 회원이 동시에 줄 서도 순번이 겹치지 않는지 확인한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QueueConcurrencyTest {

	static final long SCHEDULE_ID = 2L;

	@Autowired
	QueueService queueService;

	@Autowired
	StringRedisTemplate redis;

	@AfterEach
	void cleanUp() {
		redis.delete("queue:{" + SCHEDULE_ID + "}");
	}

	@Test
	void 회원_50명이_동시에_줄_서도_순번이_겹치지_않는다() throws Exception {
		int members = 50;
		ExecutorService pool = Executors.newFixedThreadPool(members);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (long memberId = 1; memberId <= members; memberId++) {
			long id = memberId;
			futures.add(pool.submit(() -> {
				start.await();
				return queueService.join(id, SCHEDULE_ID);
			}));
		}
		start.countDown();
		for (Future<?> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();

		// 줄 서기 응답의 순번은 명령 두 번 사이에 1~2 달라질 수 있다.
		// 그래서 모두 줄 선 뒤에 순번 확인으로 겹치지 않는지 본다.
		List<Long> positions = LongStream.rangeClosed(1, members)
			.mapToObj(id -> queueService.myPosition(id, SCHEDULE_ID).position())
			.toList();
		assertThat(positions).doesNotHaveDuplicates()
			.containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, members).boxed().toList());
	}

}
