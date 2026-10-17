package com.linkstage.seat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.queue.repository.AdmissionTestHelper;

// 시드의 1번 회차(좌석 1~50, 공연의 1회 최대 4매)와 시드 회원 1·2번으로 확인한다.
// 테스트가 만든 Redis 키와 DB 행은 끝나면 지운다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SeatHoldApiTest {

	static final long SCHEDULE_ID = 1L;

	@Autowired
	WebApplicationContext context;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		// 대기열이 켜져 있으므로 좌석을 잡을 회원 1·2를 먼저 입장 명단에 넣는다.
		AdmissionTestHelper.admit(redis, SCHEDULE_ID, 1, 2);
	}

	@AfterEach
	void cleanUp() {
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		redis.delete(redis.keys("hold_request:{" + SCHEDULE_ID + "}:*"));
		redis.delete("queue_active:{" + SCHEDULE_ID + "}");
		jdbc.update("DELETE sh FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id WHERE g.schedule_id = ?",
				SCHEDULE_ID);
		jdbc.update("DELETE FROM hold_group WHERE schedule_id = ?", SCHEDULE_ID);
		// 회차 상태를 바꾼 테스트가 있으므로 되돌린다.
		jdbc.update("UPDATE schedule SET status = 'ON_SALE' WHERE id = ?", SCHEDULE_ID);
	}

	@Test
	void 빈_좌석을_잡으면_201이고_DB_기록이_남는다() throws Exception {
		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated())
			.andExpect(jsonPath("$.requestId").value("req-1"))
			.andExpect(jsonPath("$.seatIds").value(contains(1, 2)))
			.andExpect(jsonPath("$.expiresAt").exists());

		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-1' AND status = 'HOLDING'"))
			.isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
				+ " WHERE g.request_id = 'req-1' AND sh.status = 'HELD'")).isEqualTo(2);
	}

	@Test
	void seatIds는_첫_요청과_재요청_모두_작은_번호부터_준다() throws Exception {
		hold(1, "req-1", "[3, 1, 2]").andExpect(status().isCreated())
			.andExpect(jsonPath("$.seatIds").value(contains(1, 2, 3)));

		hold(1, "req-1", "[3, 1, 2]").andExpect(status().isCreated())
			.andExpect(jsonPath("$.seatIds").value(contains(1, 2, 3)));
	}

	@Test
	void 이미_잡힌_좌석이면_409이고_아무것도_잡지_않는다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());

		hold(2, "req-2", "[1, 2]").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-2'")).isZero();
		assertThat(redis.hasKey("schedule_seat:{" + SCHEDULE_ID + "}:2")).isFalse();
	}

	@Test
	void 한_번에_최대_매수보다_많이_잡으면_400이다() throws Exception {
		hold(1, "req-1", "[1, 2, 3, 4, 5]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TOO_MANY_SEATS"));
	}

	@Test
	void 이_회차에_없는_좌석이면_400이고_좌석을_잡지_않는다() throws Exception {
		hold(1, "req-1", "[1, 999]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertThat(redis.hasKey("schedule_seat:{" + SCHEDULE_ID + "}:1")).isFalse();
	}

	@Test
	void 좌석_목록이_비어_있으면_400이다() throws Exception {
		hold(1, "req-1", "[]").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void 같은_requestId로_다시_보내면_같은_결과이고_DB_행이_늘지_않는다() throws Exception {
		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated());

		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated())
			.andExpect(jsonPath("$.seatIds").value(contains(1, 2)));
		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-1'")).isEqualTo(1);
	}

	@Test
	void 다른_회원이_쓴_requestId면_400이다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());

		hold(2, "req-1", "[2]").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertThat(redis.hasKey("schedule_seat:{" + SCHEDULE_ID + "}:2")).isFalse();
	}

	@Test
	void 풀면_RELEASED가_되고_다른_회원이_다시_잡을_수_있다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());

		release(1, "req-1").andExpect(status().isNoContent());
		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-1' AND status = 'RELEASED'"))
			.isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
				+ " WHERE g.request_id = 'req-1' AND sh.status = 'RELEASED'")).isEqualTo(1);

		hold(2, "req-2", "[1]").andExpect(status().isCreated());
	}

	@Test
	void 두_번_풀어도_204다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());

		release(1, "req-1").andExpect(status().isNoContent());
		release(1, "req-1").andExpect(status().isNoContent());
	}

	@Test
	void Redis에서_만료된_선점을_풀면_204이고_EXPIRED로_적힌다() throws Exception {
		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated());
		// 10분이 지나 좌석 키가 만료된 상태를 만든다.
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));

		release(1, "req-1").andExpect(status().isNoContent());

		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-1' AND status = 'EXPIRED'"))
			.isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
				+ " WHERE g.request_id = 'req-1' AND sh.status = 'EXPIRED'")).isEqualTo(2);
	}

	@Test
	void 만료_뒤_다른_회원이_잡은_좌석은_풀지_않고_EXPIRED로_적는다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		hold(2, "req-2", "[1]").andExpect(status().isCreated());

		release(1, "req-1").andExpect(status().isNoContent());

		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:1")).isEqualTo("HELD:req-2");
		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-1' AND status = 'EXPIRED'"))
			.isEqualTo(1);
	}

	@Test
	void 만료로_적힌_선점을_다시_풀어도_EXPIRED_그대로다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		release(1, "req-1").andExpect(status().isNoContent());
		// 좌석 키를 다시 넣는다. 두 번째 풀기가 Redis까지 가면 이 키를 지우고 RELEASED로 적어서 아래 확인이 실패한다.
		redis.opsForValue().set("schedule_seat:{" + SCHEDULE_ID + "}:1", "HELD:req-1");

		release(1, "req-1").andExpect(status().isNoContent());

		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:1")).isEqualTo("HELD:req-1");
		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-1' AND status = 'EXPIRED'"))
			.isEqualTo(1);
	}

	@Test
	void 남의_선점을_풀면_404이고_좌석과_기록은_그대로다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());

		release(2, "req-1").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:1")).isEqualTo("HELD:req-1");
		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE request_id = 'req-1' AND status = 'HOLDING'"))
			.isEqualTo(1);
	}

	@Test
	void 회원_번호_헤더가_없으면_401이다() throws Exception {
		mvc.perform(post("/api/schedules/{id}/holds", SCHEDULE_ID).contentType(MediaType.APPLICATION_JSON)
			.content("{\"requestId\": \"req-1\", \"seatIds\": [1]}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void 판매_중이_아닌_회차는_409이고_좌석을_잡지_않는다() throws Exception {
		jdbc.update("UPDATE schedule SET status = 'READY' WHERE id = ?", SCHEDULE_ID);

		hold(1, "req-1", "[1]").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_ON_SALE"));
		assertThat(redis.hasKey("schedule_seat:{" + SCHEDULE_ID + "}:1")).isFalse();
	}

	@Test
	void 처리한_뒤_판매가_끝나도_같은_requestId는_처음_결과를_준다() throws Exception {
		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated());
		jdbc.update("UPDATE schedule SET status = 'SOLD_OUT' WHERE id = ?", SCHEDULE_ID);

		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated())
			.andExpect(jsonPath("$.requestId").value("req-1"))
			.andExpect(jsonPath("$.seatIds").value(contains(1, 2)));
	}

	@Test
	void 입장_명단에_있는_회원은_좌석을_잡는다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
	}

	@Test
	void 좌석을_잡으면_입장_만료_시각이_지금_더하기_10분으로_늘어난다() throws Exception {
		long before = System.currentTimeMillis();

		hold(1, "req-1", "[1]").andExpect(status().isCreated());

		long after = System.currentTimeMillis();
		Double expireAt = redis.opsForZSet().score("queue_active:{" + SCHEDULE_ID + "}", "1");
		assertThat(expireAt).isBetween((double) before + 600_000, (double) after + 600_000);
	}

	@Test
	void 입장_명단에_없는_회원은_403이고_좌석을_잡지_않는다() throws Exception {
		redis.opsForZSet().remove("queue_active:{" + SCHEDULE_ID + "}", "1");

		hold(1, "req-1", "[1]").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ENTRY_REQUIRED"))
			.andExpect(jsonPath("$.message").value("대기열에서 입장해주세요."));
		assertThat(redis.hasKey("schedule_seat:{" + SCHEDULE_ID + "}:1")).isFalse();
	}

	@Test
	void 입장_명단에서_만료된_회원은_403이다() throws Exception {
		// 입장 만료 시각이 이미 지났다. 스케줄러가 아직 지우지 않은 상태를 만든다.
		redis.opsForZSet().add("queue_active:{" + SCHEDULE_ID + "}", "1", System.currentTimeMillis() - 1);

		hold(1, "req-1", "[1]").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ENTRY_REQUIRED"));
	}

	@Test
	void 처리한_뒤_명단에서_빠져도_같은_requestId는_처음_결과를_준다() throws Exception {
		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated());
		redis.opsForZSet().remove("queue_active:{" + SCHEDULE_ID + "}", "1");

		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated())
			.andExpect(jsonPath("$.requestId").value("req-1"))
			.andExpect(jsonPath("$.seatIds").value(contains(1, 2)));
	}

	private ResultActions hold(long memberId, String requestId, String seatIds) throws Exception {
		return mvc.perform(post("/api/schedules/{id}/holds", SCHEDULE_ID).header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"requestId\": \"" + requestId + "\", \"seatIds\": " + seatIds + "}"));
	}

	private ResultActions release(long memberId, String requestId) throws Exception {
		return mvc.perform(delete("/api/holds/{requestId}", requestId).header("X-Member-Id", memberId));
	}

	private long count(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

}
