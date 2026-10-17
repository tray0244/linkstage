package com.linkstage.common.testsupport.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.linkstage.common.exception.BusinessException;
import com.linkstage.common.exception.ErrorCode;
import com.linkstage.queue.repository.QueueStore;
import com.linkstage.show.repository.ScheduleRepository;

import lombok.RequiredArgsConstructor;

// 테스트 전용. k6를 다시 돌리기 전에 회차를 처음 상태로 되돌린다. local 프로필에서만 Bean이 생긴다.
@Service
@Profile("local")
@RequiredArgsConstructor
public class TestSupportService {

	private static final int SCAN_COUNT = 500;

	private final ScheduleRepository scheduleRepository;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transactionTemplate;

	private final StringRedisTemplate redis;

	public void resetSchedule(Long scheduleId) {
		if (!scheduleRepository.existsById(scheduleId)) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "회차를 찾을 수 없습니다: " + scheduleId);
		}
		// DB는 트랜잭션 하나로 되돌린다. payment와 seat_hold가 hold_group을 가리키므로 둘을 먼저 지운다.
		transactionTemplate.executeWithoutResult(status -> {
			jdbc.update("DELETE p FROM payment p JOIN hold_group g ON g.id = p.hold_group_id"
					+ " WHERE g.schedule_id = ?", scheduleId);
			jdbc.update("DELETE sh FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id"
					+ " WHERE g.schedule_id = ?", scheduleId);
			jdbc.update("DELETE FROM hold_group WHERE schedule_id = ?", scheduleId);
			jdbc.update("UPDATE schedule_seat SET status = 'AVAILABLE' WHERE schedule_id = ?", scheduleId);
		});
		// Redis 호출은 트랜잭션 밖에서 한다. 키 형식은 seat.repository.SeatKeys와 같다.
		// {회차ID}를 그대로 넣으므로 다른 회차(예: {20})의 키는 지워지지 않는다.
		deleteKeys("schedule_seat:{" + scheduleId + "}:*");
		deleteKeys("hold_request:{" + scheduleId + "}:*");
		// 대기열과 입장 명단은 회차마다 키가 하나씩이라 SCAN 없이 바로 지운다.
		redis.delete(List.of(QueueStore.key(scheduleId), QueueStore.activeKey(scheduleId)));
	}

	// SCAN은 키를 조금씩 나눠 훑는다. KEYS처럼 Redis를 한동안 멈추게 하지 않는다.
	private void deleteKeys(String pattern) {
		List<String> keys = new ArrayList<>();
		ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build();
		try (Cursor<String> cursor = redis.scan(options)) {
			while (cursor.hasNext()) {
				keys.add(cursor.next());
			}
		}
		if (!keys.isEmpty()) {
			redis.delete(keys);
		}
	}

}
