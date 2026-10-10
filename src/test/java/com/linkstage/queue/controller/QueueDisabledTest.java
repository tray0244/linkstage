package com.linkstage.queue.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.linkstage.TestcontainersConfiguration;

// 대기열 스위치를 끈 앱. 설정이 다르므로 Spring이 앱을 한 번 더 띄운다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.queue.enabled=false")
class QueueDisabledTest {

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
		redis.delete("queue:{" + SCHEDULE_ID + "}");
	}

	// 회차 상태를 바꾼 테스트가 있으므로 되돌린다.
	@AfterEach
	void cleanUp() {
		jdbc.update("UPDATE schedule SET status = 'ON_SALE' WHERE id = ?", SCHEDULE_ID);
	}

	@Test
	void 꺼지면_줄_서기는_바로_ADMITTED이고_Redis에_키가_생기지_않는다() throws Exception {
		mvc.perform(post("/api/schedules/{id}/queue", SCHEDULE_ID).header("X-Member-Id", 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ADMITTED"))
			.andExpect(jsonPath("$.position").value(0))
			.andExpect(jsonPath("$.entryToken").doesNotExist());

		assertThat(redis.hasKey("queue:{" + SCHEDULE_ID + "}")).isFalse();
	}

	@Test
	void 꺼지면_내_순번_확인은_404다() throws Exception {
		mvc.perform(get("/api/schedules/{id}/queue/me", SCHEDULE_ID).header("X-Member-Id", 1))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 꺼져도_판매_중이_아닌_회차에_줄_서면_409다() throws Exception {
		jdbc.update("UPDATE schedule SET status = 'READY' WHERE id = ?", SCHEDULE_ID);

		mvc.perform(post("/api/schedules/{id}/queue", SCHEDULE_ID).header("X-Member-Id", 1))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_ON_SALE"));
	}

	@Test
	void 꺼져도_없는_회차에_줄_서면_404다() throws Exception {
		mvc.perform(post("/api/schedules/{id}/queue", 999_999L).header("X-Member-Id", 1))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

}
