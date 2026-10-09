package com.linkstage.queue.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.linkstage.TestcontainersConfiguration;

// 시드의 1번 회차로 확인한다. 대기열 스위치는 기본값(켜짐)이다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QueueApiTest {

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
		redis.delete("queue:{" + SCHEDULE_ID + "}");
		redis.delete("queue_active:{" + SCHEDULE_ID + "}");
		// 회차 상태를 바꾼 테스트가 있으므로 되돌린다.
		jdbc.update("UPDATE schedule SET status = 'ON_SALE' WHERE id = ?", SCHEDULE_ID);
	}

	@Test
	void 줄_선_순서대로_순번이_1_2_3이다() throws Exception {
		join(1).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.position").value(1))
			.andExpect(jsonPath("$.entryToken").value(nullValue()));
		join(2).andExpect(jsonPath("$.position").value(2));
		join(3).andExpect(jsonPath("$.position").value(3));

		me(1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.position").value(1));
		me(3).andExpect(jsonPath("$.position").value(3));
	}

	@Test
	void 같은_회원이_다시_줄_서면_맨_뒤로_간다() throws Exception {
		join(1);
		join(2);
		join(3);
		// 점수가 밀리초 단위라서, 다시 줄 서는 시각이 앞사람들과 확실히 다르게 잠깐 기다린다.
		Thread.sleep(5);

		join(1).andExpect(jsonPath("$.position").value(3));

		me(2).andExpect(jsonPath("$.position").value(1));
		me(3).andExpect(jsonPath("$.position").value(2));
	}

	@Test
	void 입장한_회원이_다시_줄_서면_명단에서_빠지고_줄_맨_뒤로_간다() throws Exception {
		redis.opsForZSet().add("queue_active:{" + SCHEDULE_ID + "}", "1", System.currentTimeMillis() + 60_000);
		join(2);
		join(3);
		// 점수가 밀리초 단위라서, 다시 줄 서는 시각이 앞사람들과 확실히 다르게 잠깐 기다린다.
		Thread.sleep(5);

		join(1).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.position").value(3));

		assertThat(redis.opsForZSet().score("queue_active:{" + SCHEDULE_ID + "}", "1")).isNull();
		me(1).andExpect(jsonPath("$.status").value("WAITING")).andExpect(jsonPath("$.position").value(3));
	}

	@Test
	void 없는_회차에_줄_서면_404다() throws Exception {
		mvc.perform(post("/api/schedules/{id}/queue", 999_999L).header("X-Member-Id", 1))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 판매_중이_아닌_회차에_줄_서면_409이고_대기열_키가_생기지_않는다() throws Exception {
		jdbc.update("UPDATE schedule SET status = 'READY' WHERE id = ?", SCHEDULE_ID);

		join(1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_ON_SALE"));

		assertThat(redis.hasKey("queue:{" + SCHEDULE_ID + "}")).isFalse();
	}

	@Test
	void 입장_명단에_있으면_ADMITTED다() throws Exception {
		// 입장 만료 시각 = 지금 + 1분
		redis.opsForZSet().add("queue_active:{" + SCHEDULE_ID + "}", "1", System.currentTimeMillis() + 60_000);

		me(1).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ADMITTED"))
			.andExpect(jsonPath("$.position").value(0))
			.andExpect(jsonPath("$.entryToken").value(nullValue()));
	}

	@Test
	void 줄에만_있으면_WAITING이고_순번을_준다() throws Exception {
		join(1);
		join(2);

		me(2).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.position").value(2));
	}

	@Test
	void 입장_명단에서_만료된_회원은_ADMITTED가_아니다() throws Exception {
		// 입장 만료 시각이 이미 지났다. 스케줄러가 아직 지우지 않은 상태를 만든다.
		redis.opsForZSet().add("queue_active:{" + SCHEDULE_ID + "}", "1", System.currentTimeMillis() - 1);

		me(1).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 입장_명단과_줄에_모두_없으면_404다() throws Exception {
		me(1).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 줄에_없는_회원의_순번_확인은_404다() throws Exception {
		join(1);

		me(2).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	private ResultActions join(long memberId) throws Exception {
		return mvc.perform(post("/api/schedules/{id}/queue", SCHEDULE_ID).header("X-Member-Id", memberId));
	}

	private ResultActions me(long memberId) throws Exception {
		return mvc.perform(get("/api/schedules/{id}/queue/me", SCHEDULE_ID).header("X-Member-Id", memberId));
	}

}
