package com.linkstage.seat.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SeatHoldStore {

	public static final Duration HOLD_TTL = Duration.ofMinutes(10);

	private static final RedisScript<String> HOLD = RedisScript.of(new ClassPathResource("lua/seat/hold.lua"),
			String.class);

	private static final RedisScript<Long> RELEASE = RedisScript.of(new ClassPathResource("lua/seat/release.lua"),
			Long.class);

	private static final RedisScript<String> CONFIRM = RedisScript.of(new ClassPathResource("lua/seat/confirm.lua"),
			String.class);

	private final StringRedisTemplate redis;

	// 좌석을 전부 잡거나 하나도 잡지 않는다. 같은 requestId로 다시 부르면 첫 결과를 돌려준다.
	public boolean hold(long scheduleId, List<Long> seatIds, String requestId) {
		List<String> keys = new ArrayList<>(seatIds.size() + 1);
		keys.add(SeatKeys.holdRequest(scheduleId, requestId));
		for (long seatId : seatIds) {
			keys.add(SeatKeys.seat(scheduleId, seatId));
		}
		String result = redis.execute(HOLD, keys, requestId, String.valueOf(HOLD_TTL.toSeconds()));
		return "OK".equals(result);
	}

	// 선점 요청의 멱등키를 지운다. DB 저장에 실패해서 선점을 되돌릴 때만 쓴다.
	// 남겨 두면 같은 requestId로 다시 보낼 때 hold.lua가 남은 'OK'를 돌려줘서, 좌석 없이 성공 처리된다.
	public void deleteHoldRequest(long scheduleId, String requestId) {
		redis.delete(SeatKeys.holdRequest(scheduleId, requestId));
	}

	// requestId로 잡은 좌석만 푼다. 푼 좌석 수를 돌려준다. 선점 멱등키는 지우지 않는다.
	public int release(long scheduleId, List<Long> seatIds, String requestId) {
		Long released = redis.execute(RELEASE, seatKeys(scheduleId, seatIds), requestId);
		return released == null ? 0 : released.intValue();
	}

	// requestId로 잡은 좌석을 전부 확정하거나 하나도 확정하지 않는다. 확정된 좌석은 TTL이 없다.
	public boolean confirm(long scheduleId, List<Long> seatIds, String requestId) {
		String result = redis.execute(CONFIRM, seatKeys(scheduleId, seatIds), requestId);
		return "OK".equals(result);
	}

	// 좌석별 Redis 상태를 한 번에 읽는다 (MGET 1번). 키가 없는 좌석은 결과에 넣지 않는다.
	public Map<Long, SeatHoldState> states(long scheduleId, List<Long> seatIds) {
		if (seatIds.isEmpty()) {
			return Map.of();
		}
		List<String> values = redis.opsForValue().multiGet(seatKeys(scheduleId, seatIds));
		Map<Long, SeatHoldState> states = new HashMap<>();
		for (int i = 0; i < seatIds.size(); i++) {
			String value = values.get(i);
			if (value != null) {
				states.put(seatIds.get(i), value.startsWith("RESERVED:") ? SeatHoldState.RESERVED : SeatHoldState.HELD);
			}
		}
		return states;
	}

	// 좌석이 모두 이 requestId의 선점(HELD:requestId)인지 본다 (MGET 1번).
	// 키가 없거나, 다른 requestId가 잡았거나, 확정(RESERVED)된 좌석이 하나라도 있으면 false다.
	public boolean isHeldBy(long scheduleId, List<Long> seatIds, String requestId) {
		if (seatIds.isEmpty()) {
			return false;
		}
		String held = "HELD:" + requestId;
		List<String> values = redis.opsForValue().multiGet(seatKeys(scheduleId, seatIds));
		return values != null && values.stream().allMatch(held::equals);
	}

	private static List<String> seatKeys(long scheduleId, List<Long> seatIds) {
		List<String> keys = new ArrayList<>(seatIds.size());
		for (long seatId : seatIds) {
			keys.add(SeatKeys.seat(scheduleId, seatId));
		}
		return keys;
	}

}
