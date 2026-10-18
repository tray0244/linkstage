package com.linkstage.payment.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.payment.dto.PreparePaymentRequest;
import com.linkstage.payment.dto.PreparePaymentResponse;
import com.linkstage.payment.entity.Payment;
import com.linkstage.payment.repository.PaymentRepository;
import com.linkstage.seat.entity.HoldGroup;
import com.linkstage.seat.service.SeatHoldService;
import com.linkstage.show.repository.ScheduleRepository;
import com.linkstage.show.repository.ScheduleSeatRepository;

import lombok.RequiredArgsConstructor;

// 결제 준비. 토스는 부르지 않고 READY 결제만 저장한다.
// 이 클래스에는 트랜잭션을 걸지 않는다. DB 저장은 PaymentRecorder가 한다.
@Service
@RequiredArgsConstructor
public class PaymentService {

	// 토스 규칙: 주문 이름은 최대 100자
	static final int ORDER_NAME_MAX_LENGTH = 100;

	private final SeatHoldService seatHoldService;

	private final ScheduleRepository scheduleRepository;

	private final ScheduleSeatRepository scheduleSeatRepository;

	private final PaymentRepository paymentRepository;

	private final PaymentRecorder paymentRecorder;

	public PreparePaymentResponse prepare(Long memberId, PreparePaymentRequest request) {
		// 선점이 살아 있는지 먼저 본다. 결제를 준비한 뒤 선점이 만료됐으면 옛 결제를 주지 않고 409를 준다.
		HoldGroup hold = seatHoldService.getLiveHold(memberId, request.requestId());
		String orderName = orderName(hold);

		// 같은 선점으로 다시 부르면 이미 있는 결제를 그대로 준다.
		Optional<Payment> existing = paymentRepository.findByHoldGroupId(hold.getId());
		if (existing.isPresent()) {
			return toResponse(existing.get(), orderName);
		}

		Payment payment = Payment.ready(UUID.randomUUID().toString(), memberId, hold.getId(), amount(hold));
		try {
			return toResponse(paymentRecorder.save(payment), orderName);
		}
		catch (DataIntegrityViolationException e) {
			// 같은 선점으로 두 요청이 동시에 와서 다른 쪽이 먼저 저장했다. 그 결제를 준다.
			return paymentRepository.findByHoldGroupId(hold.getId())
				.map(saved -> toResponse(saved, orderName))
				.orElseThrow(() -> e);
		}
	}

	// 잡은 좌석들의 등급 가격 합계. 금액은 서버가 계산한다.
	private int amount(HoldGroup hold) {
		return scheduleSeatRepository.findAllWithGradeByIds(hold.scheduleSeatIds())
			.stream()
			.mapToInt(ss -> ss.getSeat().getSeatGrade().getPrice())
			.sum();
	}

	// "공연명 N매". 100자를 넘으면 공연명을 줄인다.
	private String orderName(HoldGroup hold) {
		String title = scheduleRepository.findWithShow(hold.getScheduleId())
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다."))
			.getShow()
			.getTitle();
		String suffix = " " + hold.scheduleSeatIds().size() + "매";
		int maxTitleLength = ORDER_NAME_MAX_LENGTH - suffix.length();
		if (title.length() > maxTitleLength) {
			title = title.substring(0, maxTitleLength);
		}
		return title + suffix;
	}

	private static PreparePaymentResponse toResponse(Payment payment, String orderName) {
		return new PreparePaymentResponse(payment.getOrderId(), payment.getAmount(), orderName);
	}

}
