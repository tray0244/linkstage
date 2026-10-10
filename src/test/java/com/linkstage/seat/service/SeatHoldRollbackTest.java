package com.linkstage.seat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.queue.repository.AdmissionTestHelper;
import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.seat.dto.HoldRequest;

// DB 저장이 실패했을 때 Redis 선점이 좌석과 멱등키 모두 되돌려지는지 확인한다.
// 멱등키가 남으면 같은 requestId 재시도가 좌석 없이 성공 처리된다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatHoldRollbackTest {

	static final long SCHEDULE_ID = 1L;

	@Autowired
	SeatHoldService service;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

	// 실제 HoldRecorder를 감싸서, 정한 호출에서만 예외를 던지게 한다.
	@MockitoSpyBean
	HoldRecorder holdRecorder;

	// 대기열이 켜져 있으므로 좌석을 잡을 회원 1·2를 먼저 입장 명단에 넣는다.
	@BeforeEach
	void admitMembers() {
		AdmissionTestHelper.admit(redis, SCHEDULE_ID, 1, 2);
	}

	@AfterEach
	void cleanUp() {
		redis.delete("queue_active:{" + SCHEDULE_ID + "}");
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		redis.delete(redis.keys("hold_request:{" + SCHEDULE_ID + "}:*"));
		jdbc.update("DELETE sh FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id WHERE g.schedule_id = ?",
				SCHEDULE_ID);
		jdbc.update("DELETE FROM hold_group WHERE schedule_id = ?", SCHEDULE_ID);
	}

	@Test
	void DB_저장이_실패하면_좌석과_멱등키가_비고_재시도가_정상이다() {
		// 첫 번째 저장만 실패하고, 두 번째부터는 실제로 저장한다.
		doThrow(new DataAccessResourceFailureException("DB 연결 끊김")).doCallRealMethod().when(holdRecorder).save(any());
		HoldRequest request = new HoldRequest("req-rollback", List.of(5L, 6L));

		assertThatThrownBy(() -> service.hold(1L, SCHEDULE_ID, request))
			.isInstanceOf(DataAccessResourceFailureException.class);
		assertThat(redis.hasKey("schedule_seat:{" + SCHEDULE_ID + "}:5")).isFalse();
		assertThat(redis.hasKey("schedule_seat:{" + SCHEDULE_ID + "}:6")).isFalse();
		assertThat(redis.hasKey("hold_request:{" + SCHEDULE_ID + "}:req-rollback")).isFalse();
		assertThat(holdingRows()).isZero();

		// 같은 requestId 재시도: Redis와 DB가 모두 잡힌 상태가 된다.
		assertThat(service.hold(1L, SCHEDULE_ID, request).seatIds()).containsExactly(5L, 6L);
		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:5")).isEqualTo("HELD:req-rollback");
		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:6")).isEqualTo("HELD:req-rollback");
		assertThat(holdingRows()).isEqualTo(1);

		// 다른 회원은 그 좌석을 잡지 못한다.
		assertThatThrownBy(() -> service.hold(2L, SCHEDULE_ID, new HoldRequest("req-other", List.of(5L))))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEAT_UNAVAILABLE));
	}

	private long holdingRows() {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-rollback' AND status = 'HOLDING'", Long.class);
	}

}
