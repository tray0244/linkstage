package com.linkstage.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.linkstage.TestcontainersConfiguration;

// Testcontainers가 띄운 실제 Redis에서 admit.lua를 검증한다. 정원 M = 100, 하드캡 = 20으로 부른다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QueueAdmitConcurrencyTest {

	static final int CAPACITY = 100;

	static final int HARD_CAP = 20;

	static final long NOW = 1_000_000L;

	static final long EXPIRE_AT = NOW + 300_000L;

	@Autowired
	QueueStore queueStore;

	@Autowired
	StringRedisTemplate redis;

	long scheduleId;

	// 테스트마다 시드에 없는 회차 번호를 써서 다른 테스트의 키와 겹치지 않게 한다.
	@BeforeEach
	void setUp() {
		scheduleId = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
	}

	@AfterEach
	void cleanUp() {
		redis.delete(List.of(QueueStore.key(scheduleId), QueueStore.activeKey(scheduleId)));
	}

	@Test
	void 한_번에_하드캡보다_많이_입장하지_않고_줄_앞사람부터_들어간다() {
		lineUp(150);

		assertThat(admit(NOW)).isEqualTo(HARD_CAP);

		assertThat(active()).hasSize(HARD_CAP).containsExactlyInAnyOrderElementsOf(members(1, HARD_CAP));
		assertThat(queueSize()).isEqualTo(150 - HARD_CAP);
	}

	@Test
	void 줄이_정원보다_길면_정원만큼만_입장한다() {
		lineUp(150);

		int total = 0;
		for (int i = 0; i < 10; i++) {
			total += admit(NOW);
		}

		assertThat(total).isEqualTo(CAPACITY);
		assertThat(active()).hasSize(CAPACITY);
		assertThat(queueSize()).isEqualTo(150 - CAPACITY);
	}

	// 정원 누수 검증: 만료된 자리가 정확히 다시 채워져야 한다. 덜 채우면 좌석이 남아도 사람이 못 들어온다.
	@Test
	void 만료된_회원은_빠지고_그_자리만큼만_다음_사람이_입장한다() {
		lineUp(150);
		for (int i = 0; i < 5; i++) {
			admit(NOW);
		}
		assertThat(active()).hasSize(CAPACITY);
		// 1~10번 회원의 입장이 이미 끝난 것으로 만든다.
		for (String member : members(1, 10)) {
			redis.opsForZSet().add(QueueStore.activeKey(scheduleId), member, NOW - 1);
		}

		assertThat(admit(NOW)).isEqualTo(10);

		assertThat(active()).hasSize(CAPACITY)
			.doesNotContainAnyElementsOf(members(1, 10))
			.containsAll(members(101, 110));
		assertThat(queueSize()).isEqualTo(150 - CAPACITY - 10);
	}

	@Test
	void 여러_스레드가_동시에_배출해도_정원을_넘지_않고_한_회원이_두_번_입장하지_않는다() throws Exception {
		int members = 500;
		int threads = 20;
		int rounds = 10;
		lineUp(members);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> futures = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			futures.add(pool.submit(() -> {
				start.await();
				int sum = 0;
				for (int r = 0; r < rounds; r++) {
					sum += admit(NOW);
				}
				return sum;
			}));
		}
		start.countDown();
		int admittedTotal = 0;
		for (Future<Integer> future : futures) {
			admittedTotal += future.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();

		Set<String> active = active();
		Set<String> waiting = redis.opsForZSet().range(QueueStore.key(scheduleId), 0, -1);
		// 배출이 보고한 인원 합계와 실제 명단 인원이 같아야 한다 (두 번 입장한 회원이 없다).
		assertThat(admittedTotal).isEqualTo(CAPACITY);
		assertThat(active).hasSize(CAPACITY);
		// 명단과 줄에 겹치는 회원이 없고, 둘을 합치면 처음 인원 그대로다 (빠진 회원이 없다).
		assertThat(active).doesNotContainAnyElementsOf(waiting);
		assertThat(active.size() + waiting.size()).isEqualTo(members);
	}

	private int admit(long now) {
		return queueStore.admit(scheduleId, now, CAPACITY, HARD_CAP, EXPIRE_AT);
	}

	// 회원 1~n번을 번호 순서대로 줄 세운다 (점수 = 번호).
	private void lineUp(int n) {
		for (int i = 1; i <= n; i++) {
			redis.opsForZSet().add(QueueStore.key(scheduleId), String.valueOf(i), i);
		}
	}

	private Set<String> active() {
		Set<String> active = redis.opsForZSet().range(QueueStore.activeKey(scheduleId), 0, -1);
		return active == null ? Set.of() : new HashSet<>(active);
	}

	private long queueSize() {
		Long size = redis.opsForZSet().zCard(QueueStore.key(scheduleId));
		return size == null ? 0 : size;
	}

	private static List<String> members(int from, int to) {
		List<String> list = new ArrayList<>();
		for (int i = from; i <= to; i++) {
			list.add(String.valueOf(i));
		}
		return list;
	}

}
