package com.linkstage.payment.dto;

// 클라이언트는 이 값으로 토스 결제창을 연다. amount는 원 단위다.
public record PreparePaymentResponse(String orderId, int amount, String orderName) {
}
