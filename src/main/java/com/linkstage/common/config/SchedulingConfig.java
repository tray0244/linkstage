package com.linkstage.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 메서드를 실제로 돌게 한다. app.queue.scheduler-enabled가 false면 이 설정이 빠져서 아무것도 돌지 않는다.
// 테스트에서는 false로 둔다. 스케줄러가 1초마다 시드 회차의 줄을 배출하면 다른 대기열 테스트가 흔들리기 때문이다.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.queue.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
