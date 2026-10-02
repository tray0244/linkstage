-- 좌석 확정. 결제가 끝난 선점을 예매 확정으로 바꾼다.
-- KEYS[1..n] = 좌석 상태 키
-- ARGV[1] = requestId
-- 반환: 'OK' 또는 'FAIL'

local held = 'HELD:' .. ARGV[1]
local reserved = 'RESERVED:' .. ARGV[1]

-- 1. 전부 내 선점인지 확인한다. 이미 내가 확정한 좌석도 통과시킨다 (같은 확정이 두 번 와도 OK).
--    만료됐거나 다른 사람이 잡은 좌석이 하나라도 있으면 아무것도 바꾸지 않는다.
for i = 1, #KEYS do
	local value = redis.call('GET', KEYS[i])
	if value ~= held and value ~= reserved then
		return 'FAIL'
	end
end

-- 2. 전부 확정으로 바꾼다. EX 없이 SET하면 TTL이 사라진다.
for i = 1, #KEYS do
	redis.call('SET', KEYS[i], reserved)
end
return 'OK'
