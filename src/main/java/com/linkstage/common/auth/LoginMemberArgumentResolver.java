package com.linkstage.common.auth;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;

// 10월 방식: X-Member-Id 헤더 값을 그대로 회원 번호로 쓴다. 검증하지 않는다 (로컬 전용).
// EC2 배포 전에 로그인 토큰을 읽도록 반드시 바꾼다.
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

	public static final String HEADER = "X-Member-Id";

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginMember.class) && parameter.getParameterType() == Long.class;
	}

	@Override
	public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		String value = webRequest.getHeader(HEADER);
		if (value == null || value.isBlank()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		try {
			return Long.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "회원 번호가 숫자가 아닙니다.");
		}
	}

}
