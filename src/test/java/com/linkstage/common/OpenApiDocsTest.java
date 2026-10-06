package com.linkstage.common;

import static org.hamcrest.Matchers.hasItems;
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

// Swagger(springdoc)가 API 문서를 만드는지 확인한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OpenApiDocsTest {

	@Autowired
	WebApplicationContext context;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void API_문서에_만든_API가_나온다() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/shows']").exists())
			.andExpect(jsonPath("$.paths['/api/schedules/{scheduleId}/seats']").exists());
	}

	@Test
	void 좌석_잡기_문서에는_X_Member_Id_헤더가_있고_memberId_인자는_없다() throws Exception {
		String hold = "$.paths['/api/schedules/{scheduleId}/holds'].post.parameters";
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(hold + "[?(@.name == 'X-Member-Id' && @.in == 'header')]").isNotEmpty())
			.andExpect(jsonPath(hold + "[?(@.name == 'memberId')]").isEmpty())
			.andExpect(jsonPath("$.paths['/api/holds/{requestId}'].delete.parameters[?(@.name == 'X-Member-Id')]")
				.isNotEmpty());
	}

	@Test
	void 문서에_한글_제목과_태그가_나온다() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("LinkStage 공연 예매 API"))
			.andExpect(jsonPath("$.tags[*].name").value(hasItems("공연", "회차 좌석", "좌석 선점", "테스트 전용")))
			.andExpect(jsonPath("$.paths['/api/schedules/{scheduleId}/holds'].post.summary").value("좌석 잡기"));
	}

}
