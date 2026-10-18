package com.linkstage.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.payment.dto.PreparePaymentRequest;
import com.linkstage.payment.dto.PreparePaymentResponse;
import com.linkstage.queue.repository.AdmissionTestHelper;
import com.linkstage.seat.dto.HoldRequest;
import com.linkstage.seat.service.SeatHoldService;

// 같은 선점으로 결제 준비가 동시에 여러 번 와도 결제는 하나만 생기는지 확인한다.
// 시드의 1번 회차와 회원 1번으로 확인한다. 테스트가 만든 Redis 키와 DB 행은 끝나면 지운다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentPrepareConcurrencyTest {

	static final long SCHEDULE_ID = 1L;

	static final int THREADS = 10;

	@Autowired
	PaymentService paymentService;

	@Autowired
	SeatHoldService seatHoldService;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

	// 대기열이 켜져 있으므로 좌석을 잡을 회원 1을 먼저 입장 명단에 넣는다.
	@BeforeEach
	void admitMember() {
		AdmissionTestHelper.admit(redis, SCHEDULE_ID, 1);
	}

	// payment가 hold_group을 가리키므로 payment를 먼저 지운다.
	@AfterEach
	void cleanUp() {
		redis.delete("queue_active:{" + SCHEDULE_ID + "}");
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		redis.delete(redis.keys("hold_request:{" + SCHEDULE_ID + "}:*"));
		jdbc.update("DELETE p FROM payment p JOIN hold_group g ON g.id = p.hold_group_id WHERE g.schedule_id = ?",
				SCHEDULE_ID);
		jdbc.update("DELETE sh FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id WHERE g.schedule_id = ?",
				SCHEDULE_ID);
		jdbc.update("DELETE FROM hold_group WHERE schedule_id = ?", SCHEDULE_ID);
	}

	// 동시에 오면 저장은 하나만 성공한다. 몇 개는 UNIQUE에 걸리고, 그 요청은 먼저 저장된 결제를 다시 읽어 준다.
	// 늦게 온 요청은 저장 전에 이미 있는 결제를 찾아 그대로 준다.
	@Test
	void 같은_선점으로_스레드_10개가_동시에_결제를_준비해도_결제는_하나이고_주문_번호가_모두_같다() throws Exception {
		seatHoldService.hold(1L, SCHEDULE_ID, new HoldRequest("req-concurrent", List.of(1L, 2L)));
		PreparePaymentRequest request = new PreparePaymentRequest("req-concurrent");

		List<PreparePaymentResponse> responses = runTogether(() -> paymentService.prepare(1L, request));

		assertThat(responses).hasSize(THREADS);
		assertThat(responses).extracting(PreparePaymentResponse::orderId).containsOnly(responses.get(0).orderId());
		assertThat(responses).extracting(PreparePaymentResponse::amount).containsOnly(110000 * 2);
		assertThat(jdbc.queryForList("SELECT p.order_id FROM payment p JOIN hold_group g ON g.id = p.hold_group_id"
				+ " WHERE g.request_id = 'req-concurrent'", String.class)).containsExactly(responses.get(0).orderId());
	}

	// 같은 작업을 스레드 THREADS개로 같은 순간에 출발시킨다. 하나라도 예외를 던지면 get()에서 테스트가 실패한다.
	private static List<PreparePaymentResponse> runTogether(Callable<PreparePaymentResponse> task)
			throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CyclicBarrier barrier = new CyclicBarrier(THREADS);
		try {
			List<Future<PreparePaymentResponse>> futures = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				futures.add(pool.submit(() -> {
					barrier.await();
					return task.call();
				}));
			}
			List<PreparePaymentResponse> responses = new ArrayList<>();
			for (Future<PreparePaymentResponse> future : futures) {
				responses.add(future.get(30, TimeUnit.SECONDS));
			}
			return responses;
		}
		finally {
			pool.shutdown();
		}
	}

}
