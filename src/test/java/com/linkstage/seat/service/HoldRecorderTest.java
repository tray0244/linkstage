package com.linkstage.seat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.queue.repository.AdmissionTestHelper;
import com.linkstage.seat.dto.HoldRequest;

// markExpired·markReleased가 HOLDING인 선점만 바꾸는지 시드의 1번 회차와 회원 1·2번으로 확인한다.
// 회원 2의 선점(req-2)은 건드리면 안 되는 다른 데이터다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HoldRecorderTest {

	static final long SCHEDULE_ID = 1L;

	@Autowired
	HoldRecorder recorder;

	@Autowired
	SeatHoldService service;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void holdSeats() {
		AdmissionTestHelper.admit(redis, SCHEDULE_ID, 1, 2);
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L, 2L)));
		service.hold(2L, SCHEDULE_ID, new HoldRequest("req-2", List.of(3L)));
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
	void 결제가_끝난_선점에_markExpired를_불러도_CONFIRMED로_남는다() {
		confirm("req-1");
		assertThat(seatCount("req-1", "CONFIRMED")).isEqualTo(2);

		assertThat(recorder.markExpired(groupId("req-1"))).isFalse();

		assertThat(status("req-1")).isEqualTo("CONFIRMED");
		assertThat(seatCount("req-1", "CONFIRMED")).isEqualTo(2);
		assertOtherHoldUntouched();
	}

	@Test
	void 결제가_끝난_선점에_markReleased를_불러도_CONFIRMED로_남는다() {
		confirm("req-1");
		assertThat(seatCount("req-1", "CONFIRMED")).isEqualTo(2);

		assertThat(recorder.markReleased(groupId("req-1"))).isFalse();

		assertThat(status("req-1")).isEqualTo("CONFIRMED");
		assertThat(seatCount("req-1", "CONFIRMED")).isEqualTo(2);
		assertOtherHoldUntouched();
	}

	@Test
	void 직접_푼_선점에_markExpired를_불러도_RELEASED로_남는다() {
		assertThat(recorder.markReleased(groupId("req-1"))).isTrue();
		assertThat(seatCount("req-1", "RELEASED")).isEqualTo(2);

		assertThat(recorder.markExpired(groupId("req-1"))).isFalse();

		assertThat(status("req-1")).isEqualTo("RELEASED");
		assertThat(seatCount("req-1", "RELEASED")).isEqualTo(2);
		assertOtherHoldUntouched();
	}

	@Test
	void HOLDING인_선점은_좌석_기록까지_EXPIRED로_바꾸고_다른_선점은_그대로다() {
		assertThat(seatCount("req-1", "HELD")).isEqualTo(2);

		assertThat(recorder.markExpired(groupId("req-1"))).isTrue();

		assertThat(status("req-1")).isEqualTo("EXPIRED");
		assertThat(seatCount("req-1", "EXPIRED")).isEqualTo(2);
		assertOtherHoldUntouched();
	}

	// 승인 단계가 아직 없어서 CONFIRMED는 직접 넣는다.
	private void confirm(String requestId) {
		jdbc.update("UPDATE hold_group SET status = 'CONFIRMED' WHERE request_id = ?", requestId);
		jdbc.update("UPDATE seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id SET sh.status = 'CONFIRMED'"
				+ " WHERE g.request_id = ?", requestId);
	}

	private void assertOtherHoldUntouched() {
		assertThat(status("req-2")).isEqualTo("HOLDING");
		assertThat(seatCount("req-2", "HELD")).isEqualTo(1);
	}

	private Long groupId(String requestId) {
		return jdbc.queryForObject("SELECT id FROM hold_group WHERE request_id = ?", Long.class, requestId);
	}

	private String status(String requestId) {
		return jdbc.queryForObject("SELECT status FROM hold_group WHERE request_id = ?", String.class, requestId);
	}

	private int seatCount(String requestId, String status) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
				+ " WHERE g.request_id = ? AND sh.status = ?", Integer.class, requestId, status);
	}

}
