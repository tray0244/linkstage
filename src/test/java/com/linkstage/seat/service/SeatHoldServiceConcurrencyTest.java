package com.linkstage.seat.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.linkstage.queue.repository.AdmissionTestHelper;
import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.seat.dto.HoldRequest;
import com.linkstage.seat.dto.HoldResponse;

// 서비스 단계에서 동시 요청을 확인한다. Lua 단계는 seat.repository의 동시성 테스트가 확인한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatHoldServiceConcurrencyTest {

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
	void 회원_2명이_같은_좌석을_동시에_잡으면_한_명만_성공한다() throws Exception {
		List<Object> results = runTogether(() -> service.hold(1L, SCHEDULE_ID, new HoldRequest("req-a", List.of(1L))),
				() -> service.hold(2L, SCHEDULE_ID, new HoldRequest("req-b", List.of(1L))));

		assertThat(results).filteredOn(HoldResponse.class::isInstance).hasSize(1);
		assertThat(results)
			.filteredOn(r -> r instanceof BusinessException e && e.getErrorCode() == ErrorCode.SEAT_UNAVAILABLE)
			.hasSize(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hold_group WHERE schedule_id = ? AND status = 'HOLDING'",
				Long.class, SCHEDULE_ID)).isEqualTo(1);
	}

	// 같은 요청이 동시에 두 번 오면 DB 저장 하나가 중복 키로 실패한다. 그때 좌석을 풀면 안 된다.
	@Test
	void 같은_requestId가_동시에_두_번_와도_둘_다_성공하고_좌석은_잡힌_채로_남는다() throws Exception {
		HoldRequest request = new HoldRequest("req-same", List.of(1L, 2L));

		List<Object> results = runTogether(() -> service.hold(1L, SCHEDULE_ID, request),
				() -> service.hold(1L, SCHEDULE_ID, request));

		assertThat(results).allMatch(HoldResponse.class::isInstance);
		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:1")).isEqualTo("HELD:req-same");
		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:2")).isEqualTo("HELD:req-same");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-same'", Long.class))
			.isEqualTo(1);
	}

	// 두 작업을 같은 순간에 출발시키고, 결과나 BusinessException을 모아 돌려준다.
	private static List<Object> runTogether(Callable<Object> first, Callable<Object> second) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CyclicBarrier barrier = new CyclicBarrier(2);
		try {
			Future<Object> a = pool.submit(() -> run(barrier, first));
			Future<Object> b = pool.submit(() -> run(barrier, second));
			return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
		}
		finally {
			pool.shutdown();
		}
	}

	private static Object run(CyclicBarrier barrier, Callable<Object> task) throws Exception {
		barrier.await();
		try {
			return task.call();
		}
		catch (BusinessException e) {
			return e;
		}
	}

}
