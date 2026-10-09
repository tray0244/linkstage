package com.linkstage.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.queue.dto.QueueResponse;
import com.linkstage.queue.repository.QueueStore;

// 줄 서기에서 ZADD와 ZRANK 사이에 배출이 끼는 경우를 일부러 만든다.
// 실제로 끼는 순간은 맞추기 어려워서, QueueStore.join이 "순번 없음"을 돌려주게 만들고 서비스의 처리를 본다.
// 시드의 1번 회차(판매 중)를 쓴다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QueueJoinRaceTest {

	static final long SCHEDULE_ID = 1L;

	@Autowired
	QueueService queueService;

	@Autowired
	StringRedisTemplate redis;

	// 실제 QueueStore를 감싸서 join만 바꾼다. isAdmitted는 실제 Redis를 본다.
	@MockitoSpyBean
	QueueStore queueStore;

	@AfterEach
	void cleanUp() {
		redis.delete(List.of(QueueStore.key(SCHEDULE_ID), QueueStore.activeKey(SCHEDULE_ID)));
	}

	@Test
	void 줄_선_직후_배출로_이미_입장했으면_ADMITTED를_준다() {
		// 배출이 끼어든 결과: 회원 1은 줄에서 꺼내져 입장 명단에 있고, join은 순번을 찾지 못했다.
		doAnswer(invocation -> {
			redis.opsForZSet().add(QueueStore.activeKey(SCHEDULE_ID), "1", System.currentTimeMillis() + 300_000);
			return Optional.empty();
		}).when(queueStore).join(eq(SCHEDULE_ID), eq(1L), anyLong());

		assertThat(queueService.join(1L, SCHEDULE_ID)).isEqualTo(QueueResponse.admittedFromQueue());
	}

	@Test
	void 줄에도_명단에도_없으면_예외를_낸다() {
		doAnswer(invocation -> Optional.empty()).when(queueStore).join(eq(SCHEDULE_ID), eq(1L), anyLong());

		assertThatThrownBy(() -> queueService.join(1L, SCHEDULE_ID)).isInstanceOf(IllegalStateException.class);
	}

}
