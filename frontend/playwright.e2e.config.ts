import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir:"./tests/e2e",
  workers:1,
  fullyParallel:false,
  timeout:90_000,
  expect:{timeout:15_000},
  use:{baseURL:process.env.E2E_BASE_URL??"http://127.0.0.1:3100",viewport:{width:1440,height:1000},acceptDownloads:true},
});
