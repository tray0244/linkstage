package com.linkstage.queue.dto;

// 줄 서기와 내 순번 확인의 공통 응답.
// 입장 토큰은 만들지 않는다. 좌석 잡기가 입장 명단(queue_active)을 직접 확인한다.
public record QueueResponse(QueueStatus status, long position) {

	public static QueueResponse waiting(long position) {
		return new QueueResponse(QueueStatus.WAITING, position);
	}

	// 입장했을 때. 줄을 서서 입장 명단에 들어갔을 때와 대기열이 꺼져 있을 때 모두 쓴다.
	public static QueueResponse admitted() {
		return new QueueResponse(QueueStatus.ADMITTED, 0);
	}

}
