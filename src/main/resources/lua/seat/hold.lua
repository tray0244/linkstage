-- 좌석 선점. 확인과 기록을 한 번에 한다.
-- KEYS[1] = 멱등키, KEYS[2..n] = 좌석 상태 키
-- ARGV[1] = requestId, ARGV[2] = TTL(초)
-- 반환: 'OK' 또는 'FAIL'

-- 1. 같은 요청이 이미 처리됐으면 그때 결과를 그대로 돌려준다
local saved = redis.call('GET', KEYS[1])
if saved then
	return saved
end

-- 2. 좌석이 하나라도 잡혀 있으면 아무것도 잡지 않고 실패
for i = 2, #KEYS do
	if redis.call('EXISTS', KEYS[i]) == 1 then
		redis.call('SET', KEYS[1], 'FAIL', 'EX', ARGV[2])
		return 'FAIL'
	end
end

-- 3. 전부 비어 있으면 전부 잡는다
for i = 2, #KEYS do
	redis.call('SET', KEYS[i], 'HELD:' .. ARGV[1], 'EX', ARGV[2])
end
redis.call('SET', KEYS[1], 'OK', 'EX', ARGV[2])
return 'OK'
