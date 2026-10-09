-- 대기열 배출. 만료 정리 → 빈자리 계산 → 줄 앞에서 꺼내 입장 명단에 넣기를 한 번에 한다.
-- KEYS[1] = 대기열 queue:{회차}, KEYS[2] = 입장 명단 queue_active:{회차}
-- ARGV[1] = 지금 시각(ms), ARGV[2] = 정원 M, ARGV[3] = 하드캡, ARGV[4] = 입장 만료 시각(ms)
-- 반환: 이번에 입장시킨 인원

-- 1. 만료된 회원을 명단에서 지운다 (점수 = 입장 만료 시각이 지금 시각 이하)
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])

-- 2·3. 빈자리만큼, 단 하드캡을 넘지 않게
local admit = tonumber(ARGV[2]) - redis.call('ZCARD', KEYS[2])
if admit > tonumber(ARGV[3]) then
	admit = tonumber(ARGV[3])
end
if admit <= 0 then
	return 0
end

-- 4. 줄 앞에서 꺼내 명단에 넣는다. ZPOPMIN은 [회원, 점수, 회원, 점수, ...]를 돌려준다
local popped = redis.call('ZPOPMIN', KEYS[1], admit)
for i = 1, #popped, 2 do
	redis.call('ZADD', KEYS[2], ARGV[4], popped[i])
end
return #popped / 2
