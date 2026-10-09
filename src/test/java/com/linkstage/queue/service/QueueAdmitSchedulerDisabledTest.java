package com.linkstage.queue.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.queue.repository.QueueStore;

// 대기열 스위치를 끈 앱. QueueDisabledTest와 설정이 같아서 Spring이 같은 앱을 다시 쓴다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.queue.enabled=false")
class QueueAdmitSchedulerDisabledTest {

	@Autowired
	QueueAdmitScheduler scheduler;

	@Autowired
	StringRedisTemplate redis;

	@AfterEach
	void cleanUp() {
		redis.delete(List.of(QueueStore.key(1L), QueueStore.activeKey(1L)));
	}

	@Test
	void 스위치가_꺼지면_배출하지_않는다() {
		for (int i = 1; i <= 30; i++) {
			redis.opsForZSet().add(QueueStore.key(1L), String.valueOf(i), i);
		}

		scheduler.admitAll();

		assertThat(redis.hasKey(QueueStore.activeKey(1L))).isFalse();
		assertThat(redis.opsForZSet().zCard(QueueStore.key(1L))).isEqualTo(30);
	}

}
