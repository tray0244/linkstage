package com.linkstage.queue.service;

import java.util.Optional;

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
			return QueueResponse.admitted();
		}
		long now = System.currentTimeMillis();
		Optional<Long> position = queueStore.join(scheduleId, memberId, now);
		if (position.isPresent()) {
			return QueueResponse.waiting(position.get());
		}
		// 줄 선 직후 순번이 없다 = 그 사이에 배출이 끼어 이미 입장했다. 입장 명단에서 확인한다.
		if (queueStore.isAdmitted(scheduleId, memberId, now)) {
			return QueueResponse.admitted();
		}
		// 줄에도 명단에도 없다. 지금 구조에서는 생길 수 없는 경우라 예외로 남긴다.
		throw new IllegalStateException("줄 선 직후 순번도 입장 기록도 없습니다: 회차 " + scheduleId + ", 회원 " + memberId);
	}

	// 폴링용이라 DB를 보지 않고 Redis만 본다. 입장 명단을 먼저 보고, 없으면 줄을 본다.
	public QueueResponse myPosition(Long memberId, Long scheduleId) {
		if (!queueProperties.enabled()) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "대기열이 꺼져 있습니다.");
		}
		if (queueStore.isAdmitted(scheduleId, memberId, System.currentTimeMillis())) {
			return QueueResponse.admitted();
		}
		return queueStore.position(scheduleId, memberId)
			.map(QueueResponse::waiting)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "줄 서 있지 않습니다."));
	}

}
