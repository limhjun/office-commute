// 정정 시각은 브라우저 timezone 이 아니라 기록의 workZone 벽시계로 입력받는다.
// 입력(YYYY-MM-DD + HH:mm)을 그 zone 의 오프셋이 붙은 ISO 문자열로 바꾸고,
// DST 로 존재하지 않는 시각(gap)과 두 번 나타나는 시각(overlap)을 구분한다.

const MINUTE = 60_000;
const DAY = 24 * 60 * MINUTE;

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatterFor(zone: string): Intl.DateTimeFormat {
  let f = formatters.get(zone);
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', {
      timeZone: zone, hourCycle: 'h23',
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
    formatters.set(zone, f);
  }
  return f;
}

interface WallClock { year: number; month: number; day: number; hour: number; minute: number; second: number }

function wallClockAt(epochMs: number, zone: string): WallClock {
  const parts = formatterFor(zone).formatToParts(new Date(epochMs));
  const get = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((p) => p.type === type)?.value);
  return { year: get('year'), month: get('month'), day: get('day'), hour: get('hour'), minute: get('minute'), second: get('second') };
}

// 해당 순간의 zone 오프셋(분). 예: Asia/Seoul → 540
function offsetMinutesAt(epochMs: number, zone: string): number {
  const w = wallClockAt(epochMs, zone);
  const asUtc = Date.UTC(w.year, w.month - 1, w.day, w.hour, w.minute, w.second);
  return Math.round((asUtc - Math.floor(epochMs / 1000) * 1000) / MINUTE);
}

// 서버 시각에는 마이크로초가 붙을 수 있다(…T23:32:40.611261+09:00). 밀리초까지만 남겨 파싱한다.
export function parseIsoMs(iso: string): number {
  return Date.parse(iso.replace(/(\.\d{3})\d+/, '$1'));
}

export function isValidZone(zone: string): boolean {
  try {
    formatterFor(zone);
    return true;
  } catch {
    return false;
  }
}

export function formatOffset(offsetMinutes: number): string {
  const sign = offsetMinutes < 0 ? '-' : '+';
  const abs = Math.abs(offsetMinutes);
  return `${sign}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`;
}

export interface ZonedInstant {
  epochMs: number;
  offsetMinutes: number;
  // 기록 zone 오프셋이 붙은 ISO-8601. 예: 2026-09-02T19:30:00+09:00
  iso: string;
}

export type ZonedResolution =
  | { kind: 'incomplete' }
  | { kind: 'invalid' }
  | { kind: 'nonexistent' }
  | { kind: 'resolved'; candidates: [ZonedInstant] | [ZonedInstant, ZonedInstant] };

const DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/;
const TIME_RE = /^(\d{2}):(\d{2})$/;

/**
 * zone 의 벽시계 date/time 을 실제 순간으로 해석한다.
 * overlap 이면 후보가 두 개(이른 순간 → 늦은 순간 순)이고, gap 이면 nonexistent 다.
 */
export function resolveZonedDateTime(date: string | null, time: string, zone: string): ZonedResolution {
  if (!date || !time) return { kind: 'incomplete' };
  const d = DATE_RE.exec(date);
  const t = TIME_RE.exec(time);
  if (!d || !t || !isValidZone(zone)) return { kind: 'invalid' };

  const [year, month, day, hour, minute] = [d[1], d[2], d[3], t[1], t[2]].map(Number);
  if (hour > 23 || minute > 59) return { kind: 'invalid' };
  const wallAsUtc = Date.UTC(year, month - 1, day, hour, minute);
  const roundTrip = new Date(wallAsUtc);
  if (roundTrip.getUTCDate() !== day || roundTrip.getUTCMonth() !== month - 1) return { kind: 'invalid' };

  // 하루 앞뒤의 오프셋이 이 시각 주변에서 가능한 오프셋의 전부다.
  const offsets = new Set([offsetMinutesAt(wallAsUtc - DAY, zone), offsetMinutesAt(wallAsUtc + DAY, zone)]);
  const candidates: ZonedInstant[] = [];
  for (const offset of offsets) {
    const epochMs = wallAsUtc - offset * MINUTE;
    if (offsetMinutesAt(epochMs, zone) !== offset) continue;
    candidates.push({ epochMs, offsetMinutes: offset, iso: `${date}T${time}:00${formatOffset(offset)}` });
  }
  candidates.sort((a, b) => a.epochMs - b.epochMs);

  if (candidates.length === 0) return { kind: 'nonexistent' };
  return { kind: 'resolved', candidates: candidates as [ZonedInstant] | [ZonedInstant, ZonedInstant] };
}

// zone 기준 오늘 날짜(YYYY-MM-DD)
export function todayInZone(zone: string, nowMs = Date.now()): string {
  const w = wallClockAt(nowMs, zone);
  return `${w.year}-${String(w.month).padStart(2, '0')}-${String(w.day).padStart(2, '0')}`;
}

// 회사 달력(Asia/Seoul) 기준 이번 달. 월 마감·발송 대상 월의 경계는 이 달력을 쓴다.
export const COMPANY_ZONE = 'Asia/Seoul';

export function companyCurrentYearMonth(nowMs = Date.now()): string {
  return todayInZone(COMPANY_ZONE, nowMs).slice(0, 7);
}

// 회사 달력 기준 마감할 수 있는 가장 최근 월(= 직전 월)
export function latestClosableYearMonth(nowMs = Date.now()): string {
  const [y, m] = companyCurrentYearMonth(nowMs).split('-').map(Number);
  const prev = m === 1 ? { y: y - 1, m: 12 } : { y, m: m - 1 };
  return `${prev.y}-${String(prev.m).padStart(2, '0')}`;
}

// ---- 입력 칸 값 ----

export interface ZonedDateTimeValue {
  date: string | null; // YYYY-MM-DD (zone 벽시계)
  time: string;        // HH:mm
  // DST overlap 으로 같은 벽시계가 두 번 나타날 때 늦은 순간을 고를지
  preferLater: boolean;
}

export function emptyZonedValue(date: string | null = null): ZonedDateTimeValue {
  return { date, time: '', preferLater: false };
}

export function zonedValueFromIso(iso: string): ZonedDateTimeValue {
  return { date: iso.slice(0, 10), time: iso.slice(11, 16), preferLater: false };
}

export function pickInstant(value: ZonedDateTimeValue, zone: string): { resolution: ZonedResolution; instant: ZonedInstant | null } {
  const resolution = resolveZonedDateTime(value.date, value.time, zone);
  if (resolution.kind !== 'resolved') return { resolution, instant: null };
  const [first, second] = resolution.candidates;
  return { resolution, instant: value.preferLater && second ? second : first };
}
