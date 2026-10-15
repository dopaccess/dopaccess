import { cloudflareTest } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.jsonc" },
      miniflare: {
        bindings: {
          API_SECRET: "test-secret-0123456789abcdef0123456789",
          GOOGLE_SA_CLIENT_EMAIL: "sa@example.iam.gserviceaccount.com",
          GOOGLE_SA_PRIVATE_KEY: "unused-in-tests",
          GA4_PROPERTY_IDS: "111,222",
        },
      },
    }),
  ],
});
