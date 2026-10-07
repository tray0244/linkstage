package com.linkstage.queue.service;

import org.springframework.stereotype.Service;

import com.linkstage.common.config.QueueProperties;
import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.queue.dto.QueueResponse;
import com.linkstage.queue.repository.QueueStore;
import com.linkstage.show.repository.ScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueService {

	private final QueueProperties queueProperties;

	private final ScheduleRepository scheduleRepository;

	private final QueueStore queueStore;

	// 판매 상태 검사, 꺼짐일 때의 회차 검사는 아직 정하지 않았다 (개발계획서 ④ "정할 것").
	public QueueResponse join(Long memberId, Long scheduleId) {
		if (!queueProperties.enabled()) {
			return QueueResponse.admittedWithoutQueue();
		}
		if (!scheduleRepository.existsById(scheduleId)) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다: " + scheduleId);
		}
		long position = queueStore.join(scheduleId, memberId, System.currentTimeMillis());
		return QueueResponse.waiting(position);
	}

	// 폴링용이라 DB를 보지 않고 Redis만 본다.
	public QueueResponse myPosition(Long memberId, Long scheduleId) {
		if (!queueProperties.enabled()) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "대기열이 꺼져 있습니다.");
		}
		return queueStore.position(scheduleId, memberId)
			.map(QueueResponse::waiting)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "줄 서 있지 않습니다."));
	}

}
