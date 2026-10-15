import { importPKCS8, SignJWT } from "jose";
import type { Env } from "./env";

const TOKEN_KEY = "token:sa";
const SCOPE = "https://www.googleapis.com/auth/analytics.readonly";
const DATA_API = "https://analyticsdata.googleapis.com/v1beta";
const ADMIN_API = "https://analyticsadmin.googleapis.com/v1beta";

export class GA4Error extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

export interface Quota {
  consumed: number;
  remaining: number;
}

export class GA4Client {
  constructor(private env: Env) {}

  async accessToken(): Promise<string> {
    const cached = await this.env.DOPA_KV.get<{ token: string; exp: number }>(TOKEN_KEY, "json");
    const nowSec = Math.floor(Date.now() / 1000);
    if (cached && cached.exp - 300 > nowSec) return cached.token;

    const pem = this.env.GOOGLE_SA_PRIVATE_KEY.replace(/\\n/g, "\n");
    const key = await importPKCS8(pem, "RS256");
    const assertion = await new SignJWT({ scope: SCOPE })
      .setProtectedHeader({ alg: "RS256", typ: "JWT" })
      .setIssuer(this.env.GOOGLE_SA_CLIENT_EMAIL)
      .setAudience("https://oauth2.googleapis.com/token")
      .setIssuedAt(nowSec)
      .setExpirationTime(nowSec + 3600)
      .sign(key);

    const res = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
    });
    if (!res.ok) throw new GA4Error(`token request failed: ${res.status}`, res.status);
    const body = (await res.json()) as { access_token: string; expires_in: number };
    const exp = nowSec + body.expires_in;
    const ttl = Math.max(60, body.expires_in - 300);
    await this.env.DOPA_KV.put(TOKEN_KEY, JSON.stringify({ token: body.access_token, exp }), {
      expirationTtl: ttl,
    });
    return body.access_token;
  }

  private async call<T>(url: string, init?: RequestInit): Promise<T> {
    const token = await this.accessToken();
    const res = await fetch(url, {
      ...init,
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
    });
    if (!res.ok) throw new GA4Error(`GA4 API ${res.status}`, res.status);
    return (await res.json()) as T;
  }

  async getTimeZone(property: string): Promise<string> {
    const body = await this.call<{ timeZone?: string }>(`${ADMIN_API}/properties/${property}`);
    return body.timeZone || "Etc/UTC";
  }

  /** 直近 30 分の activeUsers */
  async realtimeActiveUsers(property: string): Promise<{ current: number; quota?: Quota }> {
    const body = await this.call<{
      rows?: { metricValues: { value: string }[] }[];
      propertyQuota?: { tokensPerHour?: Quota };
    }>(`${DATA_API}/properties/${property}:runRealtimeReport`, {
      method: "POST",
      body: JSON.stringify({
        metrics: [{ name: "activeUsers" }],
        minuteRanges: [{ startMinutesAgo: 29, endMinutesAgo: 0 }],
        returnPropertyQuota: true,
      }),
    });
    const current = Number(body.rows?.[0]?.metricValues?.[0]?.value ?? 0);
    return { current, quota: body.propertyQuota?.tokensPerHour };
  }

  /** 過去 28 日の date × hour ごとの activeUsers */
  async hourlyActiveUsers(
    property: string,
  ): Promise<{ date: string; hour: number; activeUsers: number }[]> {
    const body = await this.call<{
      rows?: { dimensionValues: { value: string }[]; metricValues: { value: string }[] }[];
    }>(`${DATA_API}/properties/${property}:runReport`, {
      method: "POST",
      body: JSON.stringify({
        dateRanges: [{ startDate: "28daysAgo", endDate: "yesterday" }],
        dimensions: [{ name: "date" }, { name: "hour" }],
        metrics: [{ name: "activeUsers" }],
        limit: 10000,
      }),
    });
    return (body.rows ?? []).map((r) => ({
      date: r.dimensionValues[0].value,
      hour: Number(r.dimensionValues[1].value),
      activeUsers: Number(r.metricValues[0].value),
    }));
  }
}
