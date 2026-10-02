package com.linkstage.seat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class SeatHoldStore {

	static final Duration HOLD_TTL = Duration.ofMinutes(10);

	private static final RedisScript<String> HOLD = RedisScript.of(new ClassPathResource("lua/seat/hold.lua"),
			String.class);

	private static final RedisScript<Long> RELEASE = RedisScript.of(new ClassPathResource("lua/seat/release.lua"),
			Long.class);

	private final StringRedisTemplate redis;

	public SeatHoldStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

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

	// requestId로 잡은 좌석만 푼다. 푼 좌석 수를 돌려준다. 선점 멱등키는 지우지 않는다.
	public int release(long scheduleId, List<Long> seatIds, String requestId) {
		List<String> keys = new ArrayList<>(seatIds.size());
		for (long seatId : seatIds) {
			keys.add(SeatKeys.seat(scheduleId, seatId));
		}
		Long released = redis.execute(RELEASE, keys, requestId);
		return released == null ? 0 : released.intValue();
	}

}
