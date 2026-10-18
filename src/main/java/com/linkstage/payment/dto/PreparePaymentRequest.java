package com.linkstage.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// requestId는 좌석을 잡을 때 보낸 값이다.
public record PreparePaymentRequest(@NotBlank @Size(max = 64) String requestId) {
}
