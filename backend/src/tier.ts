import type { Thresholds } from "./env";

export type Tier =
  | "ERROR"
  | "KYOMU"
  | "KANKOTORI"
  | "NORMAL"
  | "WAKUWAKU"
  | "BAKU_AGE"
  | "GOD_BURST";

export function divergence(current: number, baseline: number): number {
  return ((current - baseline) / Math.max(baseline, 1)) * 100;
}

export interface TierInput {
  current: number;
  baseline: number;
  minBaseline: number;
  minBurstUsers: number;
  thresholds: Thresholds;
  tierSet: "mvp" | "full";
}

/** 00-overview.md §5 のティア判定（ERROR は呼び出し側で扱う） */
export function decideTier(i: TierInput): Tier {
  const { current, baseline, minBaseline, minBurstUsers, thresholds: t } = i;
  if (baseline < minBaseline && current < minBaseline) return "NORMAL";
  const div = divergence(current, baseline);

  if (i.tierSet === "mvp") {
    // MVP は 3 ティア: KYOMU(-30% 未満) / NORMAL / GOD_BURST(+100% 以上)
    if (div < t.kankotori) return "KYOMU";
    if (div >= t.bakuAge) return current >= minBurstUsers ? "GOD_BURST" : "NORMAL";
    return "NORMAL";
  }

  if (div < t.kyomu) return "KYOMU";
  if (div < t.kankotori) return "KANKOTORI";
  if (div < t.wakuwaku) return "NORMAL";
  if (div < t.bakuAge) return "WAKUWAKU";
  if (div < t.godBurst) return "BAKU_AGE";
  return current >= minBurstUsers ? "GOD_BURST" : "BAKU_AGE";
}
