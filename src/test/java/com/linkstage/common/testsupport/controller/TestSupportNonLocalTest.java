package com.linkstage.common.testsupport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.linkstage.TestcontainersConfiguration;

// local이 아닌 프로필로 앱을 띄우면 테스트 전용 API가 없어야 한다.
// 프로필이 다르므로 Spring이 앱을 한 번 더 띄운다 (빌드가 조금 길어진다).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("nonlocal")
class TestSupportNonLocalTest {

	@Autowired
	WebApplicationContext context;

	@Autowired
	ApplicationContext applicationContext;

	@Test
	void local이_아닌_프로필에서는_초기화_API가_404다() throws Exception {
		assertThat(applicationContext.getBeanNamesForType(TestSupportController.class)).isEmpty();

		MockMvcBuilders.webAppContextSetup(context)
			.build()
			.perform(post("/api/test/schedules/{id}/reset", 1L))
			.andExpect(status().isNotFound());
	}

}
