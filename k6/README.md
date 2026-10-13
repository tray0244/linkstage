# k6 좌석 잡기 경합 측정

VU 500명이 회차 1(좌석 50)에 각자 한 번씩 거의 동시에 좌석 잡기를 보낸다.
초과판매(같은 좌석을 두 명이 잡는 일)가 0건인지 확인한다.

| SCENARIO | 요청 | 합격 기준 |
|---|---|---|
| `multi` | VU마다 서로 다른 무작위 1~4석 | 잡힌 좌석 ≤ 50 |
| `same` | 500명 모두 같은 1석 | 잡힌 좌석 정확히 1 |

## 실행 순서

레포 루트에서 실행한다.

```bash
# 1. MySQL·Redis 띄우기
docker compose up -d

# 2. 회원 1~500번 넣기 (여러 번 실행해도 결과가 같다)
docker compose exec -T mysql mysql -ustage -pstage stage < k6/members.sql

# 3. 앱 실행 (local 프로필, 대기열 끔). 다른 터미널에서 켜 둔다
APP_QUEUE_ENABLED=false ./gradlew bootRun

# 4. k6 실행 (같은 좌석은 SCENARIO=same)
docker run --rm -e SCENARIO=multi -v "$PWD/k6:/scripts" grafana/k6:2.3.0 run /scripts/hold-race.js
```

- 앱 주소는 `BASE_URL`(기본값 `http://host.docker.internal:8080`)로 바꿀 수 있다.
- `setup()`이 실행마다 회차 1을 초기화한다(`POST /api/test/schedules/1/reset`). 앞 실행의 선점은 다음 실행 전에 지워진다.
- 대기열이 켜져 있으면 입장 명단에 없는 회원이라 모두 403이 나온다. 이때는 `status_other`에 잡힌다.

## k6 결과에서 볼 것

| 지표 | 뜻 |
|---|---|
| `status_201` / `status_409` | 잡기 성공 / 이미 잡힌 좌석 |
| `status_5xx` | 서버 오류. 0이어야 한다 |
| `status_other` | 그 밖의 응답. 0이어야 한다. 응답 코드는 경고 로그(`VU 번호: 코드 본문`)로 나온다 |
| `seats_held` | 201 응답으로 잡힌 좌석 수의 합계 |
| `http_req_duration` p(95)·p(99) | 응답 시간 |

`status_5xx`·`status_other`·`seats_held`는 threshold로 걸어 두었다. 기준을 넘으면 k6가 0이 아닌 종료 코드를 낸다.

## 실행 뒤 확인 (k6 실행 뒤 10분 안에)

선점은 10분 뒤 Redis에서 저절로 풀린다. 그 뒤에는 Redis와 DB를 비교할 수 없으므로 10분 안에 확인한다.

```bash
# ① 같은 좌석에 HOLDING 기록이 2개 이상인 줄 → 0줄
docker compose exec -T mysql mysql -ustage -pstage stage -e "
SELECT sh.schedule_seat_id, COUNT(*) AS c
FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id
WHERE g.schedule_id = 1 AND g.status = 'HOLDING'
GROUP BY sh.schedule_seat_id HAVING c > 1;"

# ② HOLDING 좌석 수 → multi는 50 이하, same은 정확히 1
docker compose exec -T mysql mysql -ustage -pstage stage -e "
SELECT COUNT(DISTINCT sh.schedule_seat_id) AS holding_seats
FROM seat_hold sh JOIN hold_group g ON g.id = sh.hold_group_id
WHERE g.schedule_id = 1 AND g.status = 'HOLDING';"

# ③ Redis에서 HELD 상태인 좌석 키 수
docker compose exec -T redis redis-cli EVAL "local n=0 for _,k in ipairs(redis.call('KEYS', ARGV[1])) do local v=redis.call('GET', k) if v and string.sub(v, 1, 5) == 'HELD:' then n = n + 1 end end return n" 0 'schedule_seat:{1}:*'
```

④ k6의 `seats_held` = ②의 좌석 수 = ③의 키 수. 셋 중 하나라도 다르면 합격 기준을 못 지킨 것이다.

③은 `KEYS`를 쓴다. 회차 1의 좌석 키는 50개뿐이라 로컬 확인용으로는 괜찮다. 운영 Redis에서는 쓰지 않는다.

## 주의

- 좌석 수가 바뀌면 `hold-race.js`의 `seats_held` threshold(지금 50)도 바꾼다.
- 앱을 띄운 뒤 첫 실행은 JIT 컴파일과 DB 연결 생성 때문에 느릴 수 있다. 결과를 기록할 때 "앱을 띄운 뒤 몇 번째 실행"인지 함께 적는다.
- k6 결과 파일은 레포에 올리지 않는다.
