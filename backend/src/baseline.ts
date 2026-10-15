import type { Env } from "./env";
import { GA4Client } from "./ga4";
import { dowOfYmd, pastDates } from "./time";

export interface Baseline {
  tz: string;
  updatedAt: string;
  slots: number[][]; // [dow 0..6][hour 0..23]
}

export function median(values: number[]): number {
  if (values.length === 0) return 0;
  const s = [...values].sort((a, b) => a - b);
  const mid = Math.floor(s.length / 2);
  return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
}

/**
 * runReport の行（date, hour, activeUsers）から曜日 × 時間の中央値を作る。
 * 行がない時間帯は 0 人として数える。
 */
export function computeSlots(
  rows: { date: string; hour: number; activeUsers: number }[],
  dates: string[],
): number[][] {
  const byKey = new Map<string, number>();
  for (const r of rows) byKey.set(`${r.date}:${r.hour}`, r.activeUsers);
  const samples: number[][][] = Array.from({ length: 7 }, () =>
    Array.from({ length: 24 }, () => [] as number[]),
  );
  for (const date of dates) {
    const dow = dowOfYmd(date);
    for (let h = 0; h < 24; h++) samples[dow][h].push(byKey.get(`${date}:${h}`) ?? 0);
  }
  return samples.map((hours) => hours.map((v) => median(v)));
}

export const baselineKey = (property: string) => `baseline:${property}`;

export async function getBaseline(env: Env, property: string): Promise<Baseline | null> {
  return env.DOPA_KV.get<Baseline>(baselineKey(property), "json");
}

export async function rebuildBaseline(
  env: Env,
  property: string,
  now = new Date(),
): Promise<Baseline> {
  const ga = new GA4Client(env);
  const tz = await ga.getTimeZone(property);
  const dates = pastDates(now, tz, 28);
  const rows = await ga.hourlyActiveUsers(property);
  const baseline: Baseline = {
    tz,
    updatedAt: now.toISOString(),
    slots: computeSlots(rows, dates),
  };
  await env.DOPA_KV.put(baselineKey(property), JSON.stringify(baseline));
  return baseline;
}

export function isStale(b: Baseline, now = new Date(), hours = 48): boolean {
  return now.getTime() - Date.parse(b.updatedAt) > hours * 3600 * 1000;
}
