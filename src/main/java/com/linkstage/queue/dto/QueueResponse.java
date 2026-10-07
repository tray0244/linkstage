package com.linkstage.queue.dto;

// 줄 서기와 내 순번 확인의 공통 응답. entryToken은 ④-3부터 채운다.
public record QueueResponse(QueueStatus status, long position, String entryToken) {

	public static QueueResponse waiting(long position) {
		return new QueueResponse(QueueStatus.WAITING, position, null);
	}

	// 대기열이 꺼져 있을 때. 줄을 서지 않고 바로 입장한다.
	public static QueueResponse admittedWithoutQueue() {
		return new QueueResponse(QueueStatus.ADMITTED, 0, null);
	}

}
