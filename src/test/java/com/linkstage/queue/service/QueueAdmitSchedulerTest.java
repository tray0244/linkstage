package com.linkstage.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.common.config.SchedulingConfig;
import com.linkstage.queue.repository.QueueStore;

// 시드의 1·2번 회차(둘 다 판매 중)로 확인한다. 테스트에서는 스케줄러가 스스로 돌지 않으므로 admitAll()을 직접 부른다.
// 정원 100, 하드캡 20 (application.yml).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QueueAdmitSchedulerTest {

	@Autowired
	QueueAdmitScheduler scheduler;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationContext context;

	// 실제 QueueStore를 감싸서, 정한 회차에서만 예외를 던지게 한다.
	@MockitoSpyBean
	QueueStore queueStore;

	@AfterEach
	void cleanUp() {
		for (long scheduleId : List.of(1L, 2L)) {
			redis.delete(List.of(QueueStore.key(scheduleId), QueueStore.activeKey(scheduleId)));
		}
		// 회차 상태를 바꾼 테스트가 있으므로 되돌린다.
		jdbc.update("UPDATE schedule SET status = 'ON_SALE' WHERE id IN (1, 2)");
	}

	@Test
	void 테스트에서는_스케줄러가_스스로_돌지_않는다() {
		assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
	}

	@Test
	void 판매_중인_회차의_줄을_하드캡만큼_배출한다() {
		lineUp(1L, 30);

		scheduler.admitAll();

		assertThat(activeSize(1L)).isEqualTo(20);
		assertThat(queueSize(1L)).isEqualTo(10);
	}

	@Test
	void 판매_중이_아닌_회차는_배출하지_않는다() {
		jdbc.update("UPDATE schedule SET status = 'READY' WHERE id = 2");
		lineUp(2L, 30);

		scheduler.admitAll();

		assertThat(activeSize(2L)).isZero();
		assertThat(queueSize(2L)).isEqualTo(30);
	}

	@Test
	void 한_회차에서_실패해도_다음_회차는_배출한다() {
		doThrow(new IllegalStateException("배출 실패 흉내")).when(queueStore)
			.admit(eq(1L), anyLong(), anyInt(), anyInt(), anyLong());
		lineUp(1L, 30);
		lineUp(2L, 30);

		scheduler.admitAll();

		assertThat(activeSize(1L)).isZero();
		assertThat(activeSize(2L)).isEqualTo(20);
	}

	private void lineUp(long scheduleId, int n) {
		for (int i = 1; i <= n; i++) {
			redis.opsForZSet().add(QueueStore.key(scheduleId), String.valueOf(i), i);
		}
	}

	private long activeSize(long scheduleId) {
		Long size = redis.opsForZSet().zCard(QueueStore.activeKey(scheduleId));
		return size == null ? 0 : size;
	}

	private long queueSize(long scheduleId) {
		Long size = redis.opsForZSet().zCard(QueueStore.key(scheduleId));
		return size == null ? 0 : size;
	}

}
