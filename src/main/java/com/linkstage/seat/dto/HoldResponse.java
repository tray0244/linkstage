package com.linkstage.seat.dto;

import java.time.LocalDateTime;
import java.util.List;

// seatIds는 항상 작은 번호부터 준다.
public record HoldResponse(String requestId, List<Long> seatIds, LocalDateTime expiresAt) {
}
