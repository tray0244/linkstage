package com.linkstage.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.linkstage.TestcontainersConfiguration;

// Testcontainers가 띄운 실제 Redis에서 extendAdmission(ZADD XX GT CH)을 확인한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QueueAdmissionExtendTest {

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
		redis.delete(QueueStore.activeKey(scheduleId));
	}

	@Test
	void 명단에_있으면_만료_시각을_늘린다() {
		redis.opsForZSet().add(QueueStore.activeKey(scheduleId), "1", 1_000);

		assertThat(queueStore.extendAdmission(scheduleId, 1L, 2_000)).isTrue();

		assertThat(score("1")).isEqualTo(2_000);
	}

	@Test
	void 명단에_없는_회원은_새로_넣지_않는다() {
		assertThat(queueStore.extendAdmission(scheduleId, 1L, 2_000)).isFalse();

		assertThat(score("1")).isNull();
	}

	@Test
	void 이미_더_긴_만료_시각은_줄이지_않는다() {
		redis.opsForZSet().add(QueueStore.activeKey(scheduleId), "1", 3_000);

		assertThat(queueStore.extendAdmission(scheduleId, 1L, 2_000)).isFalse();

		assertThat(score("1")).isEqualTo(3_000);
	}

	private Double score(String member) {
		return redis.opsForZSet().score(QueueStore.activeKey(scheduleId), member);
	}

}
