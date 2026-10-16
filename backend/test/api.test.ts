import { createExecutionContext, env, waitOnExecutionContext } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import type { Env } from "../src/env";

const e = env as unknown as Env;
const SECRET = e.API_SECRET;
let ipCounter = 0;

function req(path: string, init: RequestInit & { auth?: string | null; ip?: string } = {}) {
  const headers = new Headers(init.headers);
  if (init.auth !== null) headers.set("authorization", `Bearer ${init.auth ?? SECRET}`);
  headers.set("cf-connecting-ip", init.ip ?? `10.0.0.${++ipCounter}`);
  return new Request(`https://dopaccess.test${path}`, { ...init, headers });
}

async function call(r: Request) {
  const ctx = createExecutionContext();
  const res = await worker.fetch(r, e, ctx);
  await waitOnExecutionContext(ctx);
  return res;
}

function flatSlots(v: number) {
  return Array.from({ length: 7 }, () => Array.from({ length: 24 }, () => v));
}

interface MockOpts {
  realtime?: number | "fail";
  hourly?: number;
  tz?: string;
}

function mockGoogle(o: MockOpts) {
  const calls: string[] = [];
  vi.stubGlobal("fetch", async (input: RequestInfo | URL) => {
    const url = String(input instanceof Request ? input.url : input);
    calls.push(url);
    if (url.includes(":runRealtimeReport")) {
      if (o.realtime === "fail") return new Response("boom", { status: 500 });
      return Response.json({
        rows: [{ metricValues: [{ value: String(o.realtime ?? 0) }] }],
        propertyQuota: { tokensPerHour: { consumed: 10, remaining: 4990 } },
      });
    }
    if (url.includes(":runReport")) {
      const rows = [];
      for (let i = 1; i <= 28; i++) {
        const d = new Date(Date.now() - i * 86400000);
        const ymd = d.toISOString().slice(0, 10).replaceAll("-", "");
        for (let h = 0; h < 24; h++)
          rows.push({
            dimensionValues: [{ value: ymd }, { value: String(h).padStart(2, "0") }],
            metricValues: [{ value: String(o.hourly ?? 20) }],
          });
      }
      return Response.json({ rows });
    }
    if (url.includes("analyticsadmin")) return Response.json({ timeZone: o.tz ?? "Asia/Tokyo" });
    return new Response("unexpected", { status: 599 });
  });
  return calls;
}

beforeEach(async () => {
  // GA4 トークンを事前に入れておき、JWT 署名を省く
  await e.DOPA_KV.put(
    "token:sa",
    JSON.stringify({ token: "fake", exp: Math.floor(Date.now() / 1000) + 3600 }),
  );
});

afterEach(async () => {
  vi.unstubAllGlobals();
  const list = await e.DOPA_KV.list();
  await Promise.all(list.keys.map((k) => e.DOPA_KV.delete(k.name)));
});

describe("auth", () => {
  it("rejects missing or wrong secrets", async () => {
    expect((await call(req("/api/realtime", { auth: null }))).status).toBe(401);
    expect((await call(req("/api/realtime", { auth: "nope" }))).status).toBe(401);
  });
  it("blocks an IP after 10 failures", async () => {
    for (let i = 0; i < 10; i++) await call(req("/api/health", { auth: "bad", ip: "9.9.9.9" }));
    const res = await call(req("/api/health", { ip: "9.9.9.9" }));
    expect(res.status).toBe(429);
    expect(res.headers.get("retry-after")).toBe("900");
  });
});

describe("/api/realtime", () => {
  it("rejects properties outside the allowlist", async () => {
    mockGoogle({ realtime: 10 });
    expect((await call(req("/api/realtime?property=999"))).status).toBe(403);
  });

  it("lazily builds the baseline and judges the tier", async () => {
    const calls = mockGoogle({ realtime: 50, hourly: 20 });
    const res = await call(req("/api/realtime"));
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, any>;
    expect(body.property).toBe("111");
    expect(body.baseline).toBe(20);
    expect(body.current).toBe(50);
    expect(body.divergence).toBe(150);
    expect(body.tier).toBe("GOD_BURST"); // MVP: +100% 以上
    expect(body.timezone).toBe("Asia/Tokyo");
    expect(body.cached).toBe(false);
    expect(body.fetchedAt).toMatch(/\+09:00$/);
    expect(calls.some((u) => u.includes(":runReport"))).toBe(true);
    expect(await e.DOPA_KV.get("baseline:111")).not.toBeNull();
  });

  it("serves from cache without hitting GA4 again", async () => {
    mockGoogle({ realtime: 20, hourly: 20 });
    await call(req("/api/realtime"));
    const calls = mockGoogle({ realtime: 999, hourly: 20 });
    const body = (await (await call(req("/api/realtime"))).json()) as Record<string, any>;
    expect(body.cached).toBe(true);
    expect(body.current).toBe(20);
    expect(calls.filter((u) => u.includes(":runRealtimeReport"))).toHaveLength(0);
  });

  it("returns stale cache when GA4 fails", async () => {
    mockGoogle({ realtime: 20, hourly: 20 });
    await call(req("/api/realtime"));
    const entry = JSON.parse((await e.DOPA_KV.get("rt:111"))!);
    entry.fetchedAtMs -= 10 * 60 * 1000;
    await e.DOPA_KV.put("rt:111", JSON.stringify(entry));
    mockGoogle({ realtime: "fail", hourly: 20 });
    const res = await call(req("/api/realtime"));
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, any>;
    expect(body.stale).toBe(true);
    expect(body.current).toBe(20);
  });

  it("returns 502 when GA4 fails and nothing is cached", async () => {
    await e.DOPA_KV.put(
      "baseline:111",
      JSON.stringify({ tz: "Asia/Tokyo", updatedAt: new Date().toISOString(), slots: flatSlots(10) }),
    );
    mockGoogle({ realtime: "fail" });
    expect((await call(req("/api/realtime"))).status).toBe(502);
  });

  it("returns NORMAL when the baseline cannot be built", async () => {
    vi.stubGlobal("fetch", async (input: RequestInfo | URL) => {
      const url = String(input instanceof Request ? input.url : input);
      if (url.includes(":runRealtimeReport"))
        return Response.json({ rows: [{ metricValues: [{ value: "500" }] }] });
      return new Response("nope", { status: 500 });
    });
    const body = (await (await call(req("/api/realtime"))).json()) as Record<string, any>;
    expect(body.tier).toBe("NORMAL");
    expect(body.baseline).toBeNull();
  });

  it("backs off baseline retries for 5 minutes after a failure", async () => {
    const calls: string[] = [];
    vi.stubGlobal("fetch", async (input: RequestInfo | URL) => {
      const url = String(input instanceof Request ? input.url : input);
      calls.push(url);
      if (url.includes(":runRealtimeReport"))
        return Response.json({ rows: [{ metricValues: [{ value: "5" }] }] });
      return new Response("nope", { status: 403 });
    });
    await call(req("/api/realtime"));
    expect(await e.DOPA_KV.get("baselinefail:111")).toBe("1");
    const before = calls.filter((u) => !u.includes(":runRealtimeReport")).length;
    await e.DOPA_KV.delete("rt:111");
    await call(req("/api/realtime"));
    const after = calls.filter((u) => !u.includes(":runRealtimeReport")).length;
    expect(after).toBe(before);
  });
});

describe("/api/health and rebuild", () => {
  it("reports status", async () => {
    mockGoogle({});
    const body = (await (await call(req("/api/health"))).json()) as Record<string, any>;
    expect(body.ok).toBe(true);
    expect(body.baselines["111"].exists).toBe(false);
  });
  it("rebuilds on demand", async () => {
    mockGoogle({ hourly: 7, tz: "America/New_York" });
    const res = await call(req("/api/baseline/rebuild?property=222", { method: "POST" }));
    expect(res.status).toBe(200);
    const b = JSON.parse((await e.DOPA_KV.get("baseline:222"))!);
    expect(b.tz).toBe("America/New_York");
    expect(b.slots[0][0]).toBe(7);
  });
});
