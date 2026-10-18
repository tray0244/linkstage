package com.linkstage.seat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.queue.repository.AdmissionTestHelper;
import com.linkstage.seat.dto.HoldRequest;
import com.linkstage.seat.entity.HoldGroup;
import com.linkstage.seat.entity.HoldGroupStatus;

// 결제 준비가 부르는 getLiveHold를 시드의 1번 회차와 회원 1·2번으로 확인한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatHoldLiveHoldTest {

	static final long SCHEDULE_ID = 1L;

	@Autowired
	SeatHoldService service;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

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
	void 살아_있는_내_선점이면_그_선점을_돌려준다() {
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L, 2L)));

		HoldGroup group = service.getLiveHold(1L, "req-1");

		assertThat(group.getRequestId()).isEqualTo("req-1");
		assertThat(group.getStatus()).isEqualTo(HoldGroupStatus.HOLDING);
		assertThat(group.scheduleSeatIds()).hasSize(2);
	}

	@Test
	void 없는_선점이면_404다() {
		assertError(() -> service.getLiveHold(1L, "no-such"), ErrorCode.NOT_FOUND, "선점을 찾을 수 없습니다.");
	}

	@Test
	void 남의_선점이면_404이고_상태는_그대로다() {
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L)));

		assertError(() -> service.getLiveHold(2L, "req-1"), ErrorCode.NOT_FOUND, "선점을 찾을 수 없습니다.");
		assertThat(status("req-1")).isEqualTo("HOLDING");
	}

	@Test
	void 직접_푼_선점이면_409_HOLD_EXPIRED이고_좌석이_풀렸다고_알린다() {
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L)));
		service.release(1L, "req-1");

		assertError(() -> service.getLiveHold(1L, "req-1"), ErrorCode.HOLD_EXPIRED, "좌석이 풀렸습니다.");
		assertThat(status("req-1")).isEqualTo("RELEASED");
	}

	@Test
	void 만료로_적힌_선점이면_409_HOLD_EXPIRED다() {
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L)));
		jdbc.update("UPDATE hold_group SET status = 'EXPIRED' WHERE request_id = 'req-1'");

		assertError(() -> service.getLiveHold(1L, "req-1"), ErrorCode.HOLD_EXPIRED, "좌석이 만료되었습니다.");
	}

	@Test
	void 결제가_끝난_선점이면_409_ALREADY_PAID다() {
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L)));
		jdbc.update("UPDATE hold_group SET status = 'CONFIRMED' WHERE request_id = 'req-1'");

		assertError(() -> service.getLiveHold(1L, "req-1"), ErrorCode.ALREADY_PAID, "이미 결제가 완료되었습니다.");
		assertThat(status("req-1")).isEqualTo("CONFIRMED");
	}

	@Test
	void Redis에서_선점이_사라졌으면_409이고_EXPIRED로_적힌다() {
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L, 2L)));
		// 10분이 지나 좌석 키가 만료된 상태를 만든다.
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));

		assertError(() -> service.getLiveHold(1L, "req-1"), ErrorCode.HOLD_EXPIRED, "좌석이 만료되었습니다.");
		assertThat(status("req-1")).isEqualTo("EXPIRED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
				+ " WHERE g.request_id = 'req-1' AND sh.status = 'EXPIRED'", Integer.class)).isEqualTo(2);
	}

	@Test
	void 만료_뒤_다른_요청이_잡은_좌석이면_409이고_EXPIRED로_적고_그_좌석은_건드리지_않는다() {
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L)));
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		service.hold(2L, SCHEDULE_ID, new HoldRequest("req-2", List.of(1L)));

		assertError(() -> service.getLiveHold(1L, "req-1"), ErrorCode.HOLD_EXPIRED, "좌석이 만료되었습니다.");
		assertThat(status("req-1")).isEqualTo("EXPIRED");
		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:1")).isEqualTo("HELD:req-2");
	}

	private void assertError(ThrowingCallable call, ErrorCode code, String message) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class)
			.hasMessage(message)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(code);
	}

	private String status(String requestId) {
		return jdbc.queryForObject("SELECT status FROM hold_group WHERE request_id = ?", String.class, requestId);
	}

}
