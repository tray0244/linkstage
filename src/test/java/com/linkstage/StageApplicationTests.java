package com.linkstage;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Testcontainers로 MySQL·Redis를 띄워야 통과한다. Docker와 연결 설정이 붙는
// 2번 작업(docker-compose)에서 @Disabled를 제거한다.
@Disabled("DB·Redis 연결은 2번 작업에서 붙인다")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StageApplicationTests {

	@Test
	void contextLoads() {
	}

}
