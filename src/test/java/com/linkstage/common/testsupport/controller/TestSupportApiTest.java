package com.linkstage.common.testsupport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.linkstage.seat.repository.SeatHoldStore;

// 시드의 1·2번 회차(각 좌석 1~50)로 확인한다. 기본 프로필은 local이다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TestSupportApiTest {

	@Autowired
	WebApplicationContext context;

	@Autowired
	SeatHoldStore store;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	JdbcTemplate jdbc;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		// 대기열이 켜져 있으므로 좌석을 잡을 회원 1·2를 1·2번 회차의 입장 명단에 넣는다.
		AdmissionTestHelper.admit(redis, 1L, 1, 2);
		AdmissionTestHelper.admit(redis, 2L, 1, 2);
	}

	// 초기화 API를 쓰지 않고 직접 지운다. 테스트 대상 코드로 뒷정리를 하지 않기 위해서다.
	@AfterEach
	void cleanUp() {
		for (long scheduleId : List.of(1L, 2L)) {
			redis.delete(redis.keys("schedule_seat:{" + scheduleId + "}:*"));
			redis.delete(redis.keys("hold_request:{" + scheduleId + "}:*"));
			redis.delete("queue:{" + scheduleId + "}");
			redis.delete("queue_active:{" + scheduleId + "}");
			jdbc.update("DELETE sh FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
					+ " WHERE g.schedule_id = ?", scheduleId);
			jdbc.update("DELETE FROM hold_group WHERE schedule_id = ?", scheduleId);
			jdbc.update("UPDATE schedule_seat SET status = 'AVAILABLE' WHERE schedule_id = ?", scheduleId);
		}
	}

	@Test
	void 초기화하면_좌석이_전부_AVAILABLE이고_선점_기록과_Redis_키가_없다() throws Exception {
		hold(1, 2L, "req-1", "[1, 2]").andExpect(status().isCreated());
		store.hold(2L, List.of(3L), "req-2");
		store.confirm(2L, List.of(3L), "req-2");
		jdbc.update("UPDATE schedule_seat SET status = 'RESERVED' WHERE schedule_id = 2 AND seat_id = 4");

		reset(2L).andExpect(status().isNoContent());

		mvc.perform(get("/api/schedules/{id}/seats", 2L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.seats.length()").value(50))
			.andExpect(jsonPath("$.seats[?(@.status != 'AVAILABLE')]").isEmpty());
		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE schedule_id = 2")).isZero();
		assertThat(count("SELECT COUNT(*) FROM schedule_seat WHERE schedule_id = 2 AND status <> 'AVAILABLE'"))
			.isZero();
		assertThat(redis.keys("schedule_seat:{2}:*")).isEmpty();
		assertThat(redis.keys("hold_request:{2}:*")).isEmpty();
	}

	@Test
	void 초기화하면_같은_좌석을_다시_잡을_수_있다() throws Exception {
		hold(1, 2L, "req-1", "[1]").andExpect(status().isCreated());
		hold(2, 2L, "req-2", "[1]").andExpect(status().isConflict());

		reset(2L).andExpect(status().isNoContent());
		// 초기화가 2번 회차의 입장 명단도 지웠으므로 회원 2를 다시 입장시킨다.
		AdmissionTestHelper.admit(redis, 2L, 2);

		hold(2, 2L, "req-2", "[1]").andExpect(status().isCreated());
	}

	@Test
	void 다른_회차의_Redis_키와_DB_행은_그대로다() throws Exception {
		hold(1, 1L, "req-s1", "[1]").andExpect(status().isCreated());
		hold(1, 2L, "req-s2", "[1]").andExpect(status().isCreated());

		reset(2L).andExpect(status().isNoContent());

		assertThat(redis.opsForValue().get("schedule_seat:{1}:1")).isEqualTo("HELD:req-s1");
		assertThat(redis.opsForValue().get("hold_request:{1}:req-s1")).isEqualTo("OK");
		assertThat(count("SELECT COUNT(*) FROM hold_group WHERE schedule_id = 1 AND status = 'HOLDING'")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
				+ " WHERE g.schedule_id = 1")).isEqualTo(1);
	}

	@Test
	void 초기화하면_그_회차의_대기열_키만_지운다() throws Exception {
		redis.opsForZSet().add("queue:{1}", "1", 1);
		redis.opsForZSet().add("queue:{2}", "1", 1);

		reset(2L).andExpect(status().isNoContent());

		assertThat(redis.hasKey("queue:{2}")).isFalse();
		assertThat(redis.hasKey("queue:{1}")).isTrue();
	}

	@Test
	void 초기화하면_그_회차의_입장_명단_키만_지운다() throws Exception {
		redis.opsForZSet().add("queue_active:{1}", "1", 1);
		redis.opsForZSet().add("queue_active:{2}", "1", 1);

		reset(2L).andExpect(status().isNoContent());

		assertThat(redis.hasKey("queue_active:{2}")).isFalse();
		assertThat(redis.hasKey("queue_active:{1}")).isTrue();
	}

	@Test
	void 없는_회차는_404다() throws Exception {
		reset(999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	private ResultActions hold(long memberId, long scheduleId, String requestId, String seatIds) throws Exception {
		return mvc.perform(post("/api/schedules/{id}/holds", scheduleId).header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"requestId\": \"" + requestId + "\", \"seatIds\": " + seatIds + "}"));
	}

	private ResultActions reset(long scheduleId) throws Exception {
		return mvc.perform(post("/api/test/schedules/{id}/reset", scheduleId));
	}

	private long count(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

}
