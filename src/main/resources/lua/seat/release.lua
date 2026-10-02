-- 좌석 해제. 내가 잡은 좌석만 지운다.
-- KEYS[1..n] = 좌석 상태 키
-- ARGV[1] = requestId
-- 반환: 지운 좌석 수

local released = 0
for i = 1, #KEYS do
	-- 값이 내 선점(HELD:requestId)일 때만 지운다.
	-- 만료 뒤 다른 사람이 잡은 좌석, 확정된 좌석(RESERVED)은 건드리지 않는다.
	if redis.call('GET', KEYS[i]) == 'HELD:' .. ARGV[1] then
		redis.call('DEL', KEYS[i])
		released = released + 1
	end
end
return released
