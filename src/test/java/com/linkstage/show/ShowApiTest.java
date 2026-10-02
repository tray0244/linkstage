package com.linkstage.show;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.linkstage.TestcontainersConfiguration;

// 시드 데이터(db/seed/R__local_seed.sql)가 들어 있는 실제 MySQL로 확인한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShowApiTest {

	@Autowired
	WebApplicationContext context;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void 공연_목록에_시드_공연과_회차_2개가_나온다() throws Exception {
		mvc.perform(get("/api/shows"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == 1)].title").value("링크스테이지 콘서트"))
			.andExpect(jsonPath("$[?(@.id == 1)].maxTicketsPerOrder").value(4))
			.andExpect(jsonPath("$[?(@.id == 1)].schedules.length()").value(2))
			.andExpect(jsonPath("$[?(@.id == 1)].schedules[0].status").value("ON_SALE"));
	}

}
