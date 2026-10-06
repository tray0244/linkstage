package com.linkstage.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 컨트롤러 인자에 붙이면 요청한 회원 번호가 들어온다: @LoginMember Long memberId
// 10월에는 X-Member-Id 헤더에서, 11월에는 로그인 토큰에서 읽는다. LoginMemberArgumentResolver만 바뀐다.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginMember {
}
