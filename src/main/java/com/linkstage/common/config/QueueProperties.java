package com.linkstage.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml의 app.queue 설정.
// enabled: 대기열 스위치 (앱 전체)
// capacity: 정원 M. 입장 명단에 동시에 있을 수 있는 최대 인원
// hardCap: 하드캡. 한 번(1초) 배출에서 입장시키는 최대 인원
// admissionTtl: 입장 유지 시간. 이 시간이 지나면 입장 명단에서 빠진다
@ConfigurationProperties("app.queue")
public record QueueProperties(@DefaultValue("true") boolean enabled, @DefaultValue("100") int capacity,
		@DefaultValue("20") int hardCap, @DefaultValue("5m") Duration admissionTtl) {
}
