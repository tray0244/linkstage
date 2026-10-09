package com.linkstage.queue.service;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.linkstage.common.config.QueueProperties;
import com.linkstage.queue.repository.QueueLimitStore;
import com.linkstage.queue.repository.QueueStore;
import com.linkstage.show.entity.ScheduleStatus;
import com.linkstage.show.repository.ScheduleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 1초마다 판매 중인 회차의 대기열을 배출한다. 서버가 1대라 배출 리더 락은 두지 않는다 (설계.md 0절).
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmitScheduler {

	private final QueueProperties queueProperties;

	private final ScheduleRepository scheduleRepository;

	private final QueueStore queueStore;

	private final QueueLimitStore queueLimitStore;

	@Scheduled(fixedDelay = 1000)
	public void admitAll() {
		if (!queueProperties.enabled()) {
			return;
		}
		long now = System.currentTimeMillis();
		long expireAt = now + queueProperties.admissionTtl().toMillis();
		for (Long scheduleId : targetScheduleIds()) {
			// 한 회차에서 실패해도 다른 회차의 배출은 계속한다.
			try {
				queueStore.admit(scheduleId, now, queueLimitStore.capacity(scheduleId), queueLimitStore.hardCap(scheduleId),
						expireAt);
			}
			catch (RuntimeException e) {
				log.error("대기열 배출 실패: 회차 {}", scheduleId, e);
			}
		}
	}

	// 배출할 회차 목록을 가져오는 유일한 곳. 지금은 DB에서 판매 중(ON_SALE)인 회차를 매번 읽는다.
	// 나중에 Redis Set으로 바꾸면 이 메서드만 고친다.
	List<Long> targetScheduleIds() {
		return scheduleRepository.findIdsByStatus(ScheduleStatus.ON_SALE);
	}

}
