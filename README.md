# Dopaccess

A gamified home-screen widget for Google Analytics 4 on Android. Your site's realtime visitors show up as a crowd: quiet days look empty, spikes bring giants and fireworks.

- `backend/`: a small relay you deploy to your own Cloudflare account (Workers + Hono). It talks to the GA4 Data API with a service account and returns a "tier" for the widget.
- `android/`: the Jetpack Glance widget (coming next).

Not published on Google Play. APKs will be on GitHub Releases. MIT licensed.

Dopaccess is not affiliated with or endorsed by Google.
