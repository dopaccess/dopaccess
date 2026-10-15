import type { MiddlewareHandler } from "hono";
import type { Env } from "./env";

const FAIL_LIMIT = 10;
const BLOCK_SEC = 15 * 60;

async function sha256(s: string): Promise<ArrayBuffer> {
  return crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
}

/** 長さに依存しない定数時間比較（両方を SHA-256 にしてから比較する） */
export async function safeEqual(a: string, b: string): Promise<boolean> {
  const [ha, hb] = await Promise.all([sha256(a), sha256(b)]);
  return crypto.subtle.timingSafeEqual(ha, hb);
}

const failKey = (ip: string) => `authfail:${ip}`;

export const rateLimitAndAuth: MiddlewareHandler<{ Bindings: Env }> = async (c, next) => {
  const ip = c.req.header("cf-connecting-ip") ?? "unknown";

  if (c.env.RATE_LIMITER) {
    const { success } = await c.env.RATE_LIMITER.limit({ key: ip });
    if (!success) {
      c.header("Retry-After", "60");
      return c.json({ error: "rate_limited" }, 429);
    }
  }

  const fails = Number((await c.env.DOPA_KV.get(failKey(ip))) ?? 0);
  if (fails >= FAIL_LIMIT) {
    c.header("Retry-After", String(BLOCK_SEC));
    return c.json({ error: "blocked" }, 429);
  }

  const header = c.req.header("authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  const secret = c.env.API_SECRET ?? "";
  if (!secret || !token || !(await safeEqual(token, secret))) {
    await c.env.DOPA_KV.put(failKey(ip), String(fails + 1), { expirationTtl: BLOCK_SEC });
    return c.json({ error: "unauthorized" }, 401);
  }
  if (fails > 0) await c.env.DOPA_KV.delete(failKey(ip));
  await next();
};
