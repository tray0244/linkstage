package com.linkstage.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml의 app.queue 설정. enabled는 대기열 스위치이고 앱 전체에 적용된다.
@ConfigurationProperties("app.queue")
public record QueueProperties(@DefaultValue("true") boolean enabled) {
}
