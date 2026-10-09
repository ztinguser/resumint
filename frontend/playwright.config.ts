import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/pdf",
  fullyParallel: false,
  workers: 1,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: "http://127.0.0.1:3100",
    viewport: { width: 900, height: 1200 },
    colorScheme: "light",
  },
});
