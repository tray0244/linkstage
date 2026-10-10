package com.linkstage.seat.controller;

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

// 대기열 스위치를 끈 앱에서 좌석 잡기를 확인한다. QueueDisabledTest와 설정이 같아서 Spring이 같은 앱을 다시 쓴다.
// 시드의 1번 회차를 쓴다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.queue.enabled=false")
class SeatHoldQueueDisabledTest {

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
	}

	@AfterEach
	void cleanUp() {
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		redis.delete(redis.keys("hold_request:{" + SCHEDULE_ID + "}:*"));
		jdbc.update("DELETE sh FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id WHERE g.schedule_id = ?",
				SCHEDULE_ID);
		jdbc.update("DELETE FROM hold_group WHERE schedule_id = ?", SCHEDULE_ID);
		// 회차 상태를 바꾼 테스트가 있으므로 되돌린다.
		jdbc.update("UPDATE schedule SET status = 'ON_SALE' WHERE id = ?", SCHEDULE_ID);
	}

	@Test
	void 꺼져도_판매_중이_아닌_회차는_409다() throws Exception {
		jdbc.update("UPDATE schedule SET status = 'READY' WHERE id = ?", SCHEDULE_ID);

		hold(1, "req-1", "[1]").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_ON_SALE"));
	}

	private ResultActions hold(long memberId, String requestId, String seatIds) throws Exception {
		return mvc.perform(post("/api/schedules/{id}/holds", SCHEDULE_ID).header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"requestId\": \"" + requestId + "\", \"seatIds\": " + seatIds + "}"));
	}

}
