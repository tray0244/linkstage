package com.linkstage.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
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

import com.jayway.jsonpath.JsonPath;
import com.linkstage.TestcontainersConfiguration;
import com.linkstage.queue.repository.AdmissionTestHelper;

// 시드의 1번 회차(공연 "링크스테이지 콘서트", 좌석 1~20은 R석 110,000원, 21~50은 S석 88,000원)와
// 회원 1·2번으로 확인한다. 테스트가 만든 Redis 키와 DB 행은 끝나면 지운다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentApiTest {

	static final long SCHEDULE_ID = 1L;

	static final String SEED_TITLE = "링크스테이지 콘서트";

	static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

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

	// payment가 hold_group을 가리키므로 payment를 먼저 지운다.
	@AfterEach
	void cleanUp() {
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		redis.delete(redis.keys("hold_request:{" + SCHEDULE_ID + "}:*"));
		redis.delete("queue_active:{" + SCHEDULE_ID + "}");
		jdbc.update("DELETE p FROM payment p JOIN hold_group g ON g.id = p.hold_group_id WHERE g.schedule_id = ?",
				SCHEDULE_ID);
		jdbc.update("DELETE sh FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id WHERE g.schedule_id = ?",
				SCHEDULE_ID);
		jdbc.update("DELETE FROM hold_group WHERE schedule_id = ?", SCHEDULE_ID);
		// 공연명을 바꾼 테스트가 있으므로 되돌린다.
		jdbc.update("UPDATE `show` SET title = ? WHERE id = 1", SEED_TITLE);
	}

	@Test
	void 결제를_준비하면_200이고_READY_결제가_저장된다() throws Exception {
		hold(1, "req-1", "[1, 21]").andExpect(status().isCreated());

		prepare(1, "req-1").andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId").value(matchesPattern(UUID_PATTERN)))
			.andExpect(jsonPath("$.amount").value(110000 + 88000))
			.andExpect(jsonPath("$.orderName").value("링크스테이지 콘서트 2매"));

		assertThat(count("SELECT COUNT(*) FROM payment p JOIN hold_group g ON g.id = p.hold_group_id"
				+ " WHERE g.request_id = 'req-1' AND p.status = 'READY' AND p.amount = 198000 AND p.member_id = 1"
				+ " AND p.payment_key IS NULL")).isEqualTo(1);
	}

	@Test
	void 같은_선점으로_두_번_부르면_같은_주문_번호와_금액이고_결제는_하나다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
		String first = prepare(1, "req-1").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		prepare(1, "req-1").andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId").value((String) JsonPath.read(first, "$.orderId")))
			.andExpect(jsonPath("$.amount").value(110000))
			.andExpect(jsonPath("$.orderName").value("링크스테이지 콘서트 1매"));

		assertThat(paymentCount("req-1")).isEqualTo(1);
	}

	@Test
	void 주문_이름이_100자를_넘으면_공연명을_줄여_100자로_맞춘다() throws Exception {
		jdbc.update("UPDATE `show` SET title = ? WHERE id = 1", "가".repeat(120));
		hold(1, "req-1", "[1, 2]").andExpect(status().isCreated());

		prepare(1, "req-1").andExpect(status().isOk())
			.andExpect(jsonPath("$.orderName").value("가".repeat(97) + " 2매"));
	}

	@Test
	void 결제_준비_뒤_Redis에서_선점이_사라지면_다시_불러도_409이고_EXPIRED로_적힌다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
		prepare(1, "req-1").andExpect(status().isOk());
		// 10분이 지나 좌석 키가 만료된 상태를 만든다.
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));

		prepare(1, "req-1").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"))
			.andExpect(jsonPath("$.orderId").doesNotExist());

		assertThat(holdStatus("req-1")).isEqualTo("EXPIRED");
	}

	@Test
	void Redis에서_선점이_사라졌으면_409이고_결제를_만들지_않는다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));

		prepare(1, "req-1").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

		assertThat(holdStatus("req-1")).isEqualTo("EXPIRED");
		assertThat(paymentCount("req-1")).isZero();
	}

	@Test
	void 다른_요청이_잡은_좌석이면_409이고_결제를_만들지_않는다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		hold(2, "req-2", "[1]").andExpect(status().isCreated());

		prepare(1, "req-1").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

		assertThat(paymentCount("req-1")).isZero();
		assertThat(redis.opsForValue().get("schedule_seat:{" + SCHEDULE_ID + "}:1")).isEqualTo("HELD:req-2");
	}

	@Test
	void 없는_선점이면_404다() throws Exception {
		prepare(1, "no-such").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 남의_선점이면_404이고_결제를_만들지_않는다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());

		prepare(2, "req-1").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(paymentCount("req-1")).isZero();
	}

	@Test
	void 직접_푼_선점이면_409이고_좌석이_풀렸다고_알린다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
		mvc.perform(delete("/api/holds/{requestId}", "req-1").header("X-Member-Id", 1))
			.andExpect(status().isNoContent());

		prepare(1, "req-1").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"))
			.andExpect(jsonPath("$.message").value("좌석이 풀렸습니다."));
	}

	@Test
	void 결제가_끝난_선점이면_409_ALREADY_PAID다() throws Exception {
		hold(1, "req-1", "[1]").andExpect(status().isCreated());
		jdbc.update("UPDATE hold_group SET status = 'CONFIRMED' WHERE request_id = 'req-1'");

		prepare(1, "req-1").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_PAID"))
			.andExpect(jsonPath("$.message").value("이미 결제가 완료되었습니다."));
	}

	@Test
	void 회원_번호_헤더가_없으면_401이다() throws Exception {
		mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\": \"req-1\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void requestId가_비어_있으면_400이다() throws Exception {
		prepare(1, "").andExpect(status().isBadRequest());
	}

	private ResultActions hold(long memberId, String requestId, String seatIds) throws Exception {
		return mvc.perform(post("/api/schedules/{id}/holds", SCHEDULE_ID).header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"requestId\": \"" + requestId + "\", \"seatIds\": " + seatIds + "}"));
	}

	private ResultActions prepare(long memberId, String requestId) throws Exception {
		return mvc.perform(post("/api/payments").header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"requestId\": \"" + requestId + "\"}"));
	}

	private long paymentCount(String requestId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM payment p JOIN hold_group g ON g.id = p.hold_group_id"
				+ " WHERE g.request_id = ?", Long.class, requestId);
	}

	private String holdStatus(String requestId) {
		return jdbc.queryForObject("SELECT status FROM hold_group WHERE request_id = ?", String.class, requestId);
	}

	private long count(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

}
