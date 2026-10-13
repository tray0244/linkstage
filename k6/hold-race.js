// 좌석 잡기 경합 측정. VU 500명이 회차 1(좌석 50)에 각자 한 번씩 거의 동시에 좌석 잡기를 보낸다.
// SCENARIO=multi: VU마다 무작위 1~4석 / SCENARIO=same: 500명 모두 같은 1석.
// 앱은 대기열을 끄고(APP_QUEUE_ENABLED=false) local 프로필로 띄운다. 실행 방법은 README.md.
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const SCENARIO = __ENV.SCENARIO || 'multi';
const SCHEDULE_ID = 1;
const VUS = 500;

if (SCENARIO !== 'multi' && SCENARIO !== 'same') {
	throw new Error(`SCENARIO는 multi 또는 same이어야 한다: ${SCENARIO}`);
}

const status201 = new Counter('status_201');
const status409 = new Counter('status_409');
const status5xx = new Counter('status_5xx');
// 201·409·5xx가 아닌 응답. 응답 코드는 status 태그와 경고 로그로 남긴다.
const statusOther = new Counter('status_other');
// 201 응답으로 잡힌 좌석 수의 합계
const seatsHeld = new Counter('seats_held');

export const options = {
	scenarios: {
		hold_race: {
			executor: 'per-vu-iterations',
			vus: VUS,
			iterations: 1,
			maxDuration: '1m',
		},
	},
	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
	// 실패로 끝나면 k6가 0이 아닌 종료 코드를 낸다. DB·Redis 확인은 README의 명령으로 따로 한다.
	thresholds: {
		status_5xx: ['count==0'],
		status_other: ['count==0'],
		seats_held: [SCENARIO === 'same' ? 'count==1' : 'count<=50'],
	},
};

// 409(이미 잡힌 좌석)는 경합에서 정상 결과이므로 http_req_failed에서 실패로 세지 않는다.
http.setResponseCallback(http.expectedStatuses(201, 409));

export function setup() {
	const reset = http.post(`${BASE_URL}/api/test/schedules/${SCHEDULE_ID}/reset`);
	if (reset.status !== 204) {
		exec.test.abort(`회차 초기화 실패: ${reset.status} ${reset.body}`);
	}
	const seats = http.get(`${BASE_URL}/api/schedules/${SCHEDULE_ID}/seats`);
	if (seats.status !== 200) {
		exec.test.abort(`좌석 현황 조회 실패: ${seats.status} ${seats.body}`);
	}
	const seatIds = seats.json('seats').map((seat) => seat.seatId).sort((a, b) => a - b);
	// runId를 여기서 한 번만 만들어 모든 VU가 같은 값을 쓰게 한다. requestId가 실행마다 달라진다.
	return { runId: Date.now(), seatIds };
}

export default function (data) {
	const vu = exec.vu.idInTest;
	const seatIds = SCENARIO === 'same' ? [data.seatIds[0]] : pickRandom(data.seatIds, 1 + Math.floor(Math.random() * 4));
	const res = http.post(
		`${BASE_URL}/api/schedules/${SCHEDULE_ID}/holds`,
		JSON.stringify({ requestId: `${data.runId}-${vu}`, seatIds }),
		{ headers: { 'Content-Type': 'application/json', 'X-Member-Id': String(vu) } },
	);

	// 한 번도 늘지 않은 카운터도 요약에 0으로 나오게 한다.
	[status201, status409, status5xx, statusOther, seatsHeld].forEach((counter) => counter.add(0));
	if (res.status === 201) {
		status201.add(1);
		seatsHeld.add(res.json('seatIds').length);
	} else if (res.status === 409) {
		status409.add(1);
	} else if (res.status >= 500) {
		status5xx.add(1, { status: String(res.status) });
		console.warn(`VU ${vu}: ${res.status} ${res.body}`);
	} else {
		statusOther.add(1, { status: String(res.status) });
		console.warn(`VU ${vu}: ${res.status} ${res.body}`);
	}
}

// 서로 다른 좌석 count개를 무작위로 고른다.
function pickRandom(ids, count) {
	const pool = ids.slice();
	const picked = [];
	for (let i = 0; i < count; i++) {
		picked.push(pool.splice(Math.floor(Math.random() * pool.length), 1)[0]);
	}
	return picked;
}
