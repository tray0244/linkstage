package com.linkstage.queue.service;

import org.springframework.stereotype.Service;

import com.linkstage.common.config.QueueProperties;
import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.queue.dto.QueueResponse;
import com.linkstage.queue.repository.QueueStore;
import com.linkstage.show.entity.Schedule;
import com.linkstage.show.repository.ScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueService {

	private final QueueProperties queueProperties;

	private final ScheduleRepository scheduleRepository;

	private final QueueStore queueStore;

	// 대기열 스위치와 관계없이 회차를 먼저 검사한다. 회차 조회는 한 번으로 있는지와 판매 중인지를 함께 본다.
	public QueueResponse join(Long memberId, Long scheduleId) {
		Schedule schedule = scheduleRepository.findById(scheduleId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다: " + scheduleId));
		if (!schedule.isOnSale()) {
			throw new BusinessException(ErrorCode.NOT_ON_SALE, "판매 중인 회차가 아닙니다: " + scheduleId);
		}
		if (!queueProperties.enabled()) {
			return QueueResponse.admittedWithoutQueue();
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
