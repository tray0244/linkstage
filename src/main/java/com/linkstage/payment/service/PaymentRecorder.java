package com.linkstage.payment.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.linkstage.payment.entity.Payment;
import com.linkstage.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

// 결제의 DB 저장만 맡는다. PaymentService와 다른 클래스에 둔 이유:
// 같은 클래스 안에서 @Transactional 메서드를 부르면 트랜잭션이 걸리지 않는다.
@Component
@RequiredArgsConstructor
class PaymentRecorder {

	private final PaymentRepository paymentRepository;

	// saveAndFlush: INSERT를 바로 실행한다. 중복 키 오류가 이 메서드 안에서 나게 하려는 것이다.
	@Transactional
	public Payment save(Payment payment) {
		return paymentRepository.saveAndFlush(payment);
	}

}
