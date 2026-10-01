package com.linkstage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Testcontainers가 MySQL·Redis를 직접 띄운다. docker-compose는 필요 없다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StageApplicationTests {

	@Test
	void contextLoads() {
	}

}
