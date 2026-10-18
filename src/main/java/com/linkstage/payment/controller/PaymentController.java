package com.linkstage.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.linkstage.common.auth.LoginMember;
import com.linkstage.payment.dto.PreparePaymentRequest;
import com.linkstage.payment.dto.PreparePaymentResponse;
import com.linkstage.payment.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "결제", description = "잡은 좌석을 토스페이먼츠로 결제한다. X-Member-Id 헤더가 필요하다")
@RestController
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	@Operation(summary = "결제 준비", description = "잡은 좌석의 주문 번호·금액·주문 이름을 준다. 이 값으로 토스 결제창을 연다. 같은 선점으로 다시 부르면 같은 결제를 준다. 성공하면 200, 선점이 없거나 남의 것이면 404 NOT_FOUND, 만료되거나 풀렸으면 409 HOLD_EXPIRED, 이미 결제가 끝났으면 409 ALREADY_PAID.")
	@PostMapping("/api/payments")
	public PreparePaymentResponse prepare(@LoginMember Long memberId, @Valid @RequestBody PreparePaymentRequest request) {
		return paymentService.prepare(memberId, request);
	}

}
