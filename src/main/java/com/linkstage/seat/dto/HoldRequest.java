package com.linkstage.seat.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// requestId는 클라이언트가 좌석을 고를 때마다 새로 만든다 (멱등키, 최대 64자).
public record HoldRequest(@NotBlank @Size(max = 64) String requestId, @NotEmpty List<@NotNull Long> seatIds) {
}
