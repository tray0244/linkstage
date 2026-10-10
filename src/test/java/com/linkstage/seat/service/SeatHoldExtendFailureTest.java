package com.linkstage.seat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.queue.repository.AdmissionTestHelper;
import com.linkstage.queue.repository.QueueStore;
import com.linkstage.seat.dto.HoldRequest;

// 입장 유지 시간 늘리기가 실패해도 좌석 잡기는 성공으로 끝나는지 확인한다.
// QueueStore를 감싸서 extendAdmission만 실패하게 만든다. 같은 설정을 쓰는 다른 테스트의 앱을 Spring이 다시 쓴다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatHoldExtendFailureTest {

	static final long SCHEDULE_ID = 1L;

	@Autowired
	SeatHoldService service;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	QueueStore queueStore;

	@BeforeEach
	void admitMember() {
		AdmissionTestHelper.admit(redis, SCHEDULE_ID, 1);
	}

	@AfterEach
	void cleanUp() {
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		redis.delete(redis.keys("hold_request:{" + SCHEDULE_ID + "}:*"));
		redis.delete(QueueStore.activeKey(SCHEDULE_ID));
		jdbc.update("DELETE sh FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id WHERE g.schedule_id = ?",
				SCHEDULE_ID);
		jdbc.update("DELETE FROM hold_group WHERE schedule_id = ?", SCHEDULE_ID);
	}

	@Test
	void 입장_유지_시간_늘리기가_실패해도_좌석_잡기는_성공한다() {
		doThrow(new IllegalStateException("늘리기 실패 흉내")).when(queueStore)
			.extendAdmission(anyLong(), anyLong(), anyLong());

		assertThat(service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L))).seatIds()).containsExactly(1L);

		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:1")).isEqualTo("HELD:req-1");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-1' AND status = 'HOLDING'",
				Long.class)).isEqualTo(1);
	}

}
