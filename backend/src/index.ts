import { Hono } from "hono";
import { rateLimitAndAuth } from "./auth";
import { getBaseline, rebuildBaseline } from "./baseline";
import { loadConfig, type Env } from "./env";
import { GA4Client } from "./ga4";
import { getRealtime } from "./realtime";
import { zonedParts } from "./time";

const app = new Hono<{ Bindings: Env }>();

app.use("/api/*", rateLimitAndAuth);

function resolveProperty(env: Env, requested: string | undefined): string | null | undefined {
  const { propertyIds } = loadConfig(env);
  if (!requested) return propertyIds[0];
  return propertyIds.includes(requested) ? requested : null;
}

app.get("/api/realtime", async (c) => {
  const property = resolveProperty(c.env, c.req.query("property"));
  if (property === undefined) return c.json({ error: "no_property_configured" }, 500);
  if (property === null) return c.json({ error: "forbidden_property" }, 403);
  const scale = c.req.query("scale") === "absolute" ? "absolute" : "relative";
  const { status, body } = await getRealtime(c.env, loadConfig(c.env), property, {
    scale,
    waitUntil: (p) => c.executionCtx.waitUntil(p),
  });
  return c.json(body, status);
});

app.get("/api/health", async (c) => {
  const cfg = loadConfig(c.env);
  const result: Record<string, unknown> = { kv: false, token: false, baselines: {} };
  try {
    await c.env.DOPA_KV.get("health:ping");
    result.kv = true;
  } catch {}
  try {
    await new GA4Client(c.env).accessToken();
    result.token = true;
  } catch (e) {
    result.tokenError = String(e);
  }
  const baselines: Record<string, unknown> = {};
  for (const p of cfg.propertyIds) {
    const b = await getBaseline(c.env, p);
    baselines[p] = b ? { exists: true, updatedAt: b.updatedAt, tz: b.tz } : { exists: false };
  }
  result.baselines = baselines;
  const ok = result.kv === true && result.token === true;
  return c.json({ ok, ...result }, ok ? 200 : 503);
});

app.post("/api/baseline/rebuild", async (c) => {
  const property = resolveProperty(c.env, c.req.query("property"));
  if (property === undefined) return c.json({ error: "no_property_configured" }, 500);
  if (property === null) return c.json({ error: "forbidden_property" }, 403);
  try {
    const b = await rebuildBaseline(c.env, property);
    return c.json({ ok: true, property, tz: b.tz, updatedAt: b.updatedAt });
  } catch (e) {
    return c.json({ ok: false, error: String(e) }, 502);
  }
});

app.notFound((c) => c.json({ error: "not_found" }, 404));

export default {
  fetch: app.fetch,
  /** 毎時起動し、プロパティのタイムゾーンで 03 時台のときだけ再計算する */
  async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext) {
    const now = new Date();
    for (const p of loadConfig(env).propertyIds) {
      const b = await getBaseline(env, p);
      const due = !b || zonedParts(now, b.tz).hour === 3;
      if (due) {
        ctx.waitUntil(rebuildBaseline(env, p, now).catch((e) => console.warn("cron rebuild failed", p, String(e))));
      }
    }
  },
} satisfies ExportedHandler<Env>;

export { app };
