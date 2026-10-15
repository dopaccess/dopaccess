export interface Env {
  DOPA_KV: KVNamespace;
  RATE_LIMITER?: { limit(opts: { key: string }): Promise<{ success: boolean }> };
  API_SECRET: string;
  GOOGLE_SA_CLIENT_EMAIL: string;
  GOOGLE_SA_PRIVATE_KEY: string;
  GA4_PROPERTY_IDS: string;
  MIN_BASELINE?: string;
  MIN_BURST_USERS?: string;
  TIER_SET?: string;
  TIER_THRESHOLDS?: string;
  CACHE_TTL_SEC?: string;
  REALTIME_TO_HOURLY_K?: string;
}

export interface Config {
  propertyIds: string[];
  minBaseline: number;
  minBurstUsers: number;
  tierSet: "mvp" | "full";
  thresholds: Thresholds;
  cacheTtlSec: number;
  k: number;
}

export interface Thresholds {
  kyomu: number;
  kankotori: number;
  wakuwaku: number;
  bakuAge: number;
  godBurst: number;
}

export const DEFAULT_THRESHOLDS: Thresholds = {
  kyomu: -60,
  kankotori: -30,
  wakuwaku: 30,
  bakuAge: 100,
  godBurst: 300,
};

function num(v: string | undefined, d: number): number {
  if (v === undefined || v.trim() === "") return d;
  const n = Number(v);
  return Number.isFinite(n) ? n : d;
}

export function loadConfig(env: Env): Config {
  let thresholds = DEFAULT_THRESHOLDS;
  if (env.TIER_THRESHOLDS && env.TIER_THRESHOLDS.trim() !== "") {
    try {
      thresholds = { ...DEFAULT_THRESHOLDS, ...JSON.parse(env.TIER_THRESHOLDS) };
    } catch {
      thresholds = DEFAULT_THRESHOLDS;
    }
  }
  return {
    propertyIds: (env.GA4_PROPERTY_IDS ?? "")
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean),
    minBaseline: num(env.MIN_BASELINE, 5),
    minBurstUsers: num(env.MIN_BURST_USERS, 10),
    tierSet: env.TIER_SET === "full" ? "full" : "mvp",
    thresholds,
    cacheTtlSec: Math.max(60, num(env.CACHE_TTL_SEC, 60)),
    k: num(env.REALTIME_TO_HOURLY_K, 1.0),
  };
}
