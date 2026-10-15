# Dopaccess backend (Cloudflare Workers)

Requires Node.js 22+.

```sh
npm install
npx wrangler kv namespace create DOPA_KV   # put the id into wrangler.jsonc
npx wrangler secret put API_SECRET           # 32+ random bytes, e.g. `openssl rand -base64 32`
npx wrangler secret put GOOGLE_SA_CLIENT_EMAIL
npx wrangler secret put GOOGLE_SA_PRIVATE_KEY
# set GA4_PROPERTY_IDS in wrangler.jsonc (comma separated, first is the default)
npm run deploy
```

Give the service account the **Viewer** role on your GA4 property, then:

```sh
curl -X POST -H "Authorization: Bearer $API_SECRET" https://<your-worker>/api/baseline/rebuild
curl -H "Authorization: Bearer $API_SECRET" https://<your-worker>/api/realtime
```

## API

- `GET /api/realtime?property=<id>&scale=relative|absolute` returns tier, current, baseline, divergence, and crowd size.
- `GET /api/health` checks KV, the Google token, and baselines.
- `POST /api/baseline/rebuild?property=<id>` recalculates the baseline (median of the last 4 weeks, same weekday and hour).

`TIER_SET` is `mvp` (KYOMU / NORMAL / GOD_BURST) by default; set it to `full` for all six tiers.

## Development

```sh
cp .dev.vars.example .dev.vars
npm run dev
npm test
```
