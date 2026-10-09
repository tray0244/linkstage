package com.linkstage.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.linkstage.common.config.QueueProperties;

// Spring을 띄우지 않고 확인한다.
class QueueLimitStoreTest {

	@Test
	void 설정의_정원과_하드캡을_돌려준다() {
		QueueLimitStore store = new QueueLimitStore(new QueueProperties(true, 100, 20, Duration.ofMinutes(5)));

		assertThat(store.capacity(1L)).isEqualTo(100);
		assertThat(store.hardCap(1L)).isEqualTo(20);
	}

}
