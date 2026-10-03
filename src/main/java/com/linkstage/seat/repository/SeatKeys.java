package com.linkstage.seat.repository;

// Redis 키 이름은 여기서만 만든다.
// {회차ID}는 Redis Cluster 해시태그다. 같은 회차의 키가 같은 슬롯에 모인다.
final class SeatKeys {

	private SeatKeys() {
	}

	// 좌석 상태. 키가 없으면 비어 있는 좌석이다.
	static String seat(long scheduleId, long seatId) {
		return "schedule_seat:{" + scheduleId + "}:" + seatId;
	}

	// 선점 요청 멱등키.
	static String holdRequest(long scheduleId, String requestId) {
		return "hold_request:{" + scheduleId + "}:" + requestId;
	}

}
