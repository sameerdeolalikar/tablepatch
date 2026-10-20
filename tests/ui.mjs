import { chromium } from "playwright";
import assert from "node:assert/strict";
import { readFile, mkdtemp, writeFile, mkdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { execFileSync } from "node:child_process";
const browser = await chromium.launch({ headless: true });
const directory = await mkdtemp(join(tmpdir(), "tablepatch-ui-"));
try {
  const page = await browser.newPage({
      viewport: { width: 1440, height: 1000 },
    }),
    errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("http://127.0.0.1:7411");
  await page.locator("#demo").click();
  await page.locator("#review").waitFor({ state: "visible" });
  assert.equal(await page.locator("#count-modified").textContent(), "2");
  assert.equal(await page.locator("#count-added").textContent(), "1");
  assert.equal(await page.locator("#count-removed").textContent(), "1");
  await page
    .getByLabel("Version for A1", { exact: true })
    .selectOption("before");
  await page
    .getByLabel("Version for C3", { exact: true })
    .selectOption("before");
  await page.locator("#search").fill("Notebook");
  assert.equal(await page.locator("#rows tr").count(), 1);
  await page.locator("#search").fill("");
  await page.locator("#filter").selectOption("changes");
  assert.equal(await page.locator("#rows tr").count(), 4);
  const csvEvent = page.waitForEvent("download");
  await page.locator("#export-csv").click();
  const csvDownload = await csvEvent;
  const output = await readFile(await csvDownload.path(), "utf8");
  assert.match(output, /A1,Notebook,12/);
  assert.match(output, /C3,Canvas bag,8/);
  assert.match(output, /D4,Paper clips,40/);
  const patchEvent = page.waitForEvent("download");
  await page.locator("#export-patch").click();
  const patchDownload = await patchEvent;
  await writeFile(
    join(directory, "before.csv"),
    await page.locator("#before").inputValue(),
  );
  await patchDownload.saveAs(join(directory, "patch.csv"));
  const java = process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, "bin", "java")
    : "java";
  execFileSync(java, [
    "-jar",
    "build/tablepatch.jar",
    "apply",
    join(directory, "before.csv"),
    join(directory, "patch.csv"),
    "--output",
    join(directory, "result.csv"),
  ]);
  assert.equal(await readFile(join(directory, "result.csv"), "utf8"), output);
  if (process.env.UI_SCREENSHOTS) {
    await mkdir(process.env.UI_SCREENSHOTS, { recursive: true });
    await page.screenshot({
      path: join(process.env.UI_SCREENSHOTS, "tablepatch-desktop.png"),
      fullPage: true,
    });
  }
  await page.setViewportSize({ width: 390, height: 844 });
  assert(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  if (process.env.UI_SCREENSHOTS)
    await page.screenshot({
      path: join(process.env.UI_SCREENSHOTS, "tablepatch-mobile.png"),
      fullPage: true,
    });
  await page.locator("#key").fill("missing");
  assert(await page.locator("#export-csv").isDisabled());
  await page.locator("#compare").click();
  await page.locator("#error").waitFor({ state: "visible" });
  assert.match(
    await page.locator("#error").textContent(),
    /key column not found/,
  );
  const denied = await page.request.post("http://127.0.0.1:7411/api/compare", {
    form: { before: "a\n1", after: "a\n2", key: "a" },
  });
  assert.equal(denied.status(), 400);
  assert.deepEqual(errors, []);
  console.log(
    "PASS TablePatch: review, decisions, filters, CSV/patch round trip, mobile layout, validation, request boundary.",
  );
} finally {
  await browser.close();
  await rm(directory, { recursive: true, force: true });
}
