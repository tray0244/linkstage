package com.linkstage.show;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.seat.repository.SeatHoldStore;

// 시드의 2번 회차(좌석 1~50)로 확인한다. 테스트가 바꾼 Redis 키와 DB 행은 끝나면 되돌린다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduleSeatApiTest {

	static final long SCHEDULE_ID = 2L;

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
	}

	@AfterEach
	void cleanUp() {
		redis.delete(redis.keys("schedule_seat:{" + SCHEDULE_ID + "}:*"));
		redis.delete(redis.keys("hold_request:{" + SCHEDULE_ID + "}:*"));
		jdbc.update("UPDATE schedule_seat SET status = 'AVAILABLE' WHERE schedule_id = ?", SCHEDULE_ID);
	}

	@Test
	void 아무도_잡지_않은_회차는_좌석_50개가_모두_AVAILABLE이다() throws Exception {
		mvc.perform(get("/api/schedules/{id}/seats", SCHEDULE_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scheduleId").value(SCHEDULE_ID))
			.andExpect(jsonPath("$.seats.length()").value(50))
			.andExpect(jsonPath("$.seats[?(@.status != 'AVAILABLE')]").isEmpty())
			.andExpect(jsonPath("$.seats[0].seatId").value(1))
			.andExpect(jsonPath("$.seats[0].rowLabel").value("A"))
			.andExpect(jsonPath("$.seats[0].grade").value("R"))
			.andExpect(jsonPath("$.seats[0].price").value(110000))
			.andExpect(jsonPath("$.seats[49].rowLabel").value("E"))
			.andExpect(jsonPath("$.seats[49].grade").value("S"));
	}

	@Test
	void 잡은_좌석은_HELD_확정한_좌석은_RESERVED로_나온다() throws Exception {
		store.hold(SCHEDULE_ID, List.of(1L), "held");
		store.hold(SCHEDULE_ID, List.of(2L), "reserved");
		store.confirm(SCHEDULE_ID, List.of(2L), "reserved");

		mvc.perform(get("/api/schedules/{id}/seats", SCHEDULE_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.seats[0].status").value("HELD"))
			.andExpect(jsonPath("$.seats[1].status").value("RESERVED"))
			.andExpect(jsonPath("$.seats[2].status").value("AVAILABLE"));
	}

	@Test
	void Redis에_키가_없어도_DB가_RESERVED면_RESERVED로_나온다() throws Exception {
		jdbc.update("UPDATE schedule_seat SET status = 'RESERVED' WHERE schedule_id = ? AND seat_id = 3", SCHEDULE_ID);

		mvc.perform(get("/api/schedules/{id}/seats", SCHEDULE_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.seats[2].status").value("RESERVED"));
	}

	@Test
	void 없는_회차는_404를_돌려준다() throws Exception {
		mvc.perform(get("/api/schedules/{id}/seats", 999_999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

}
