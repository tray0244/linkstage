package com.linkstage.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;

// Spring을 띄우지 않고 리졸버만 확인한다.
class LoginMemberArgumentResolverTest {

	LoginMemberArgumentResolver resolver = new LoginMemberArgumentResolver();

	@Test
	void 헤더의_회원_번호를_돌려준다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Member-Id", "1");

		assertThat(resolver.resolveArgument(null, null, new ServletWebRequest(request), null)).isEqualTo(1L);
	}

	@Test
	void 헤더가_없으면_UNAUTHORIZED다() {
		ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}

	@Test
	void 숫자가_아니면_UNAUTHORIZED다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Member-Id", "abc");

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}

}
