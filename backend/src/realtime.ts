import type { Baseline } from "./baseline";
import { getBaseline, isStale, rebuildBaseline } from "./baseline";
import { absoluteCrowd, relativeCrowd, type Crowd } from "./crowd";
import type { Config, Env } from "./env";
import { GA4Client, type Quota } from "./ga4";
import { decideTier, divergence, type Tier } from "./tier";
import { isoInZone, zonedParts } from "./time";

export interface RealtimeResponse {
  property: string;
  tier: Tier;
  current: number;
  baseline: number | null;
  divergence: number;
  crowd: Crowd;
  timezone: string;
  slot: { dow: number; hour: number };
  baselineUpdatedAt: string | null;
  fetchedAt: string;
  cached: boolean;
  stale?: boolean;
}

interface CacheEntry {
  current: number;
  fetchedAtMs: number;
  ttlSec: number;
}

const cacheKey = (p: string) => `rt:${p}`;
const round1 = (n: number) => Math.round(n * 10) / 10;

export function quotaTtl(cfg: Config, quota?: Quota): number {
  if (!quota) return cfg.cacheTtlSec;
  const total = quota.consumed + quota.remaining;
  if (total > 0 && quota.remaining / total < 0.1) return Math.max(cfg.cacheTtlSec, 300);
  return cfg.cacheTtlSec;
}

export async function ensureBaseline(
  env: Env,
  property: string,
  waitUntil: (p: Promise<unknown>) => void,
): Promise<Baseline | null> {
  const b = await getBaseline(env, property);
  if (!b) {
    try {
      return await rebuildBaseline(env, property);
    } catch (e) {
      console.warn("baseline lazy init failed", String(e));
      return null;
    }
  }
  if (isStale(b)) {
    waitUntil(rebuildBaseline(env, property).catch((e) => console.warn("baseline refresh failed", String(e))));
  }
  return b;
}

export async function getRealtime(
  env: Env,
  cfg: Config,
  property: string,
  opts: { scale: "relative" | "absolute"; waitUntil: (p: Promise<unknown>) => void; now?: Date },
): Promise<{ status: 200 | 502; body: RealtimeResponse | { error: string } }> {
  const now = opts.now ?? new Date();
  const baseline = await ensureBaseline(env, property, opts.waitUntil);

  const cached = await env.DOPA_KV.get<CacheEntry>(cacheKey(property), "json");
  let entry: CacheEntry | null = null;
  let fromCache = false;
  let stale = false;

  if (cached && now.getTime() - cached.fetchedAtMs < cached.ttlSec * 1000) {
    entry = cached;
    fromCache = true;
  } else {
    try {
      const ga = new GA4Client(env);
      const { current, quota } = await ga.realtimeActiveUsers(property);
      entry = { current, fetchedAtMs: now.getTime(), ttlSec: quotaTtl(cfg, quota) };
      // stale 応答用に 1 日は残しておく（新鮮さは fetchedAtMs で判定）
      await env.DOPA_KV.put(cacheKey(property), JSON.stringify(entry), { expirationTtl: 86400 });
      console.log(`realtime ${property} current=${current}`);
    } catch (e) {
      console.warn("realtime fetch failed", String(e));
      if (!cached) return { status: 502, body: { error: "ga4_unavailable" } };
      entry = cached;
      fromCache = true;
      stale = true;
    }
  }

  const tz = baseline?.tz ?? "Etc/UTC";
  const slot = zonedParts(now, tz);
  const current = entry.current;
  const base = baseline ? round1(baseline.slots[slot.dow][slot.hour] * cfg.k) : null;

  const tier: Tier =
    base === null
      ? "NORMAL"
      : decideTier({
          current,
          baseline: base,
          minBaseline: cfg.minBaseline,
          minBurstUsers: cfg.minBurstUsers,
          thresholds: cfg.thresholds,
          tierSet: cfg.tierSet,
        });

  const body: RealtimeResponse = {
    property,
    tier,
    current,
    baseline: base,
    divergence: base === null ? 0 : round1(divergence(current, base)),
    crowd: opts.scale === "absolute" ? absoluteCrowd(current) : relativeCrowd(current, base ?? current),
    timezone: tz,
    slot: { dow: slot.dow, hour: slot.hour },
    baselineUpdatedAt: baseline ? isoInZone(new Date(baseline.updatedAt), tz) : null,
    fetchedAt: isoInZone(new Date(entry.fetchedAtMs), tz),
    cached: fromCache,
  };
  if (stale) body.stale = true;
  return { status: 200, body };
}
