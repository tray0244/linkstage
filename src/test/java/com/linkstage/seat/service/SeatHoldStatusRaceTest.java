package com.linkstage.seat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.queue.repository.AdmissionTestHelper;
import com.linkstage.seat.dto.HoldRequest;
import com.linkstage.seat.repository.SeatHoldStore;

// 선점 상태를 읽은 뒤 쓰기 전에 다른 요청이 상태를 먼저 바꾸는 경우를 확인한다.
// 그냥 동시에 부르면 어느 쪽이 먼저 끝날지 정해지지 않는다. 그래서 SeatHoldStore를 spy로 감싸 순서를 고정한다.
// 회원 2의 선점(req-2)은 건드리면 안 되는 다른 데이터다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatHoldStatusRaceTest {

	static final long SCHEDULE_ID = 1L;

	@Autowired
	SeatHoldService service;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	SeatHoldStore seatHoldStore;

	@BeforeEach
	void holdSeats() {
		AdmissionTestHelper.admit(redis, SCHEDULE_ID, 1, 2);
		service.hold(1L, SCHEDULE_ID, new HoldRequest("req-1", List.of(1L, 2L)));
		service.hold(2L, SCHEDULE_ID, new HoldRequest("req-2", List.of(3L)));
		assertThat(status("req-1")).isEqualTo("HOLDING");
		assertThat(seatCount("req-1", "HELD")).isEqualTo(2);
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
	void 결제_준비가_Redis를_보는_사이에_좌석을_풀면_RELEASED로_남고_좌석이_풀렸다고_알린다() throws Exception {
		CountDownLatch prepareRead = new CountDownLatch(1);
		CountDownLatch releaseDone = new CountDownLatch(1);
		// 결제 준비는 HOLDING을 읽은 뒤, 좌석 풀기가 다 끝날 때까지 기다렸다가 Redis를 본다.
		doAnswer(invocation -> {
			prepareRead.countDown();
			assertThat(releaseDone.await(10, TimeUnit.SECONDS)).isTrue();
			return invocation.callRealMethod();
		}).when(seatHoldStore).isHeldBy(anyLong(), anyList(), eq("req-1"));

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> prepare = executor.submit(() -> service.getLiveHold(1L, "req-1"));
			assertThat(prepareRead.await(10, TimeUnit.SECONDS)).isTrue();
			service.release(1L, "req-1");
			assertThat(status("req-1")).isEqualTo("RELEASED");
			releaseDone.countDown();

			assertError(() -> getCause(prepare), ErrorCode.HOLD_EXPIRED, "좌석이 풀렸습니다.");
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(status("req-1")).isEqualTo("RELEASED");
		assertThat(seatCount("req-1", "RELEASED")).isEqualTo(2);
		assertOtherHoldUntouched();
	}

	@Test
	void 결제_준비가_Redis를_보는_사이에_결제가_끝나면_409_ALREADY_PAID이고_CONFIRMED로_남는다() {
		// 승인 단계가 아직 없어서, 결제가 끝나 좌석 키가 바뀐 상황을 흉내 낸다.
		doAnswer(invocation -> {
			confirm("req-1");
			return false;
		}).when(seatHoldStore).isHeldBy(anyLong(), anyList(), eq("req-1"));

		assertError(() -> service.getLiveHold(1L, "req-1"), ErrorCode.ALREADY_PAID, "이미 결제가 완료되었습니다.");

		assertThat(status("req-1")).isEqualTo("CONFIRMED");
		assertThat(seatCount("req-1", "CONFIRMED")).isEqualTo(2);
		assertOtherHoldUntouched();
	}

	@Test
	void 좌석을_푸는_사이에_결제가_끝나면_400이고_CONFIRMED로_남는다() {
		doAnswer(invocation -> {
			Object released = invocation.callRealMethod();
			confirm("req-1");
			return released;
		}).when(seatHoldStore).release(anyLong(), anyList(), eq("req-1"));

		assertError(() -> service.release(1L, "req-1"), ErrorCode.INVALID_REQUEST, "결제가 끝난 좌석은 풀 수 없습니다.");

		assertThat(status("req-1")).isEqualTo("CONFIRMED");
		assertThat(seatCount("req-1", "CONFIRMED")).isEqualTo(2);
		assertOtherHoldUntouched();
	}

	@Test
	void 좌석을_푸는_사이에_결제_준비가_EXPIRED로_적으면_오류_없이_끝나고_EXPIRED로_남는다() {
		doAnswer(invocation -> {
			Object released = invocation.callRealMethod();
			setStatus("req-1", "EXPIRED");
			return released;
		}).when(seatHoldStore).release(anyLong(), anyList(), eq("req-1"));

		service.release(1L, "req-1");

		assertThat(status("req-1")).isEqualTo("EXPIRED");
		assertThat(seatCount("req-1", "EXPIRED")).isEqualTo(2);
		assertOtherHoldUntouched();
	}

	// 승인 단계가 아직 없어서 CONFIRMED는 직접 넣는다.
	private void confirm(String requestId) {
		setStatus(requestId, "CONFIRMED");
	}

	// 다른 요청이 hold_group과 seat_hold를 함께 바꾼 것을 흉내 낸다.
	private void setStatus(String requestId, String status) {
		jdbc.update("UPDATE hold_group SET status = ? WHERE request_id = ?", status, requestId);
		jdbc.update("UPDATE seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id SET sh.status = ?"
				+ " WHERE g.request_id = ?", status, requestId);
	}

	// Future.get()은 예외를 ExecutionException으로 감싼다. 안의 예외를 꺼낸다.
	private static void getCause(Future<?> future) throws Throwable {
		try {
			future.get(10, TimeUnit.SECONDS);
		}
		catch (ExecutionException e) {
			throw e.getCause();
		}
	}

	private void assertError(ThrowingCallable call, ErrorCode code, String message) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class)
			.hasMessage(message)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(code);
	}

	private void assertOtherHoldUntouched() {
		assertThat(status("req-2")).isEqualTo("HOLDING");
		assertThat(seatCount("req-2", "HELD")).isEqualTo(1);
	}

	private String status(String requestId) {
		return jdbc.queryForObject("SELECT status FROM hold_group WHERE request_id = ?", String.class, requestId);
	}

	private int seatCount(String requestId, String status) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
				+ " WHERE g.request_id = ? AND sh.status = ?", Integer.class, requestId, status);
	}

}
