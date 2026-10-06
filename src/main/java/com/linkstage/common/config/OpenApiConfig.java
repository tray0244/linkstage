package com.linkstage.common.config;

import java.util.Arrays;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.linkstage.common.auth.LoginMember;
import com.linkstage.common.auth.LoginMemberArgumentResolver;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;

// Swagger 설정. 문서 맨 위의 제목과 설명을 정한다.
// @LoginMember 인자는 요청 값으로 보여주지 않고, 그 대신 X-Member-Id 헤더 입력 칸을 보여준다.
@Configuration
public class OpenApiConfig {

	static {
		SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginMember.class);
	}

	public static final String TITLE = "LinkStage 공연 예매 API";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI().info(new Info().title(TITLE)
			.version("v1")
			.description("대기열 → 좌석 선점 → 결제로 이어지는 공연 티켓 예매 백엔드. 10월은 로컬 전용이고, X-Member-Id 헤더로 회원을 구분한다."));
	}

	@Bean
	public OperationCustomizer loginMemberHeader() {
		return (operation, handlerMethod) -> {
			boolean needsMember = Arrays.stream(handlerMethod.getMethodParameters())
				.anyMatch(p -> p.hasParameterAnnotation(LoginMember.class));
			if (needsMember) {
				operation.addParametersItem(new HeaderParameter().name(LoginMemberArgumentResolver.HEADER)
					.required(true)
					.description("회원 번호 (10월 로컬 전용, 검증하지 않음)")
					.schema(new StringSchema().example("1")));
			}
			return operation;
		};
	}

}
