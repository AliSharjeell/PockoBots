import { defineConfig, devices } from "@playwright/test";
export default defineConfig({
  testDir: "./ui-test",
  workers: 1,
  fullyParallel: false,
  timeout: 30000,
  use: { baseURL: "http://127.0.0.1:18888", trace: "retain-on-failure" },
  projects: [
    { name: "android-mobile", use: { ...devices["Pixel 7"] } },
    { name: "desktop", use: { viewport: { width: 1280, height: 900 } } },
  ],
});
