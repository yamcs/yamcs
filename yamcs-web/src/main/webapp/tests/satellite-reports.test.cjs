const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { test } = require("node:test");

const directory = "../projects/webapp/src/app/satellite-reports/";

const snapshot = {
  id: "report-1",
  type: "11-10",
  status: "COMPLETE",
  sortTime: "2026-11-23T08:00:00Z",
  finalizedAt: "2026-11-23T08:00:10Z",
};

test("a decoded empty response stays Complete with a received page and zero rows", () => {
  const { summarizeReport } = require(directory + "report-view-model.ts");
  const summary = summarizeReport({
    ...snapshot,
    expectedPages: 1,
    receivedPages: [1],
    pages: [{ pageNumber: 1 }],
  });
  assert.equal(summary.label, "Complete");
  assert.equal(summary.received, 1);
  assert.equal(summary.rows.length, 0);
});

test("packet timestamps show the APID and source sequence counter", () => {
  const template = readFileSync(
    require.resolve(directory + "report-snapshot.component.html"),
    "utf8",
  );
  assert.match(template, /<th scope="col">APID<\/th>/);
  assert.match(template, /<th scope="col">Source sequence counter<\/th>/);
  assert.ok(template.includes('page.apid ?? "—"'));
  assert.ok(template.includes('page.sourceSequenceCount ?? "—"'));
  assert.doesNotMatch(template, /<th scope="col">Sequence<\/th>/);
});

test("binary report values share one three-dot menu across row and nested fields", () => {
  const template = readFileSync(
    require.resolve(directory + "report-snapshot.component.html"),
    "utf8",
  );
  const binaryCase = template
    .split('@case ("BINARY")')[1]
    ?.split('@case ("TIMESTAMP")')[0];
  assert.ok(binaryCase);
  assert.match(binaryCase, /<ya-more icon="more_horiz"/);
  assert.match(binaryCase, /downloadBinary\(bytes, 'hex', filename\)/);
  assert.match(binaryCase, /downloadBinary\(bytes, 'bin', filename\)/);
  assert.equal(binaryCase.match(/<code>/g)?.length, 1);
  assert.doesNotMatch(binaryCase, /<details>/);
  assert.doesNotMatch(template, /column\.name === "tc_packet_bytes"/);
  assert.match(template, /\$implicit: cell\?\.value/);
  assert.match(template, /\$implicit: entry/);
  assert.match(template, /\$implicit: value\.aggregateValue\.value\[\$index\]/);
});

test("partial reports keep confidently decoded rows in page order and compact missing ranges", () => {
  const { summarizeReport } = require(directory + "report-view-model.ts");
  const row = (n) => ({ cells: [{ name: "apid", value: { uint32Value: n } }] });
  const summary = summarizeReport({
    ...snapshot,
    status: "PARTIAL",
    expectedPages: 7,
    receivedPages: [2, 5],
    missingPages: [1, 3, 4, 6, 7],
    pages: [
      { pageNumber: 2, rows: [row(4), row(3)] },
      { pageNumber: 5, rows: [row(2)] },
    ],
  });
  assert.equal(summary.label, "Partial");
  assert.equal(summary.missing, "1, 3–4, 6–7");
  assert.deepEqual(
    summary.rows.map(({ pageNumber, cells }) => [
      pageNumber,
      cells.apid.value.uint32Value,
    ]),
    [
      [2, 4],
      [2, 3],
      [5, 2],
    ],
  );
});

test("ambiguity and no response do not invent page counts or attribute withheld packets", () => {
  const { summarizeReport } = require(directory + "report-view-model.ts");
  const ambiguous = summarizeReport({
    ...snapshot,
    status: "AMBIGUOUS",
    withheldPackets: 2,
  });
  assert.equal(ambiguous.label, "Ambiguous");
  assert.equal(ambiguous.received, 0);
  assert.equal(ambiguous.expected, undefined);
  assert.equal(ambiguous.withheld, 2);
  const noResponse = summarizeReport({ ...snapshot, status: "NO_RESPONSE" });
  assert.equal(noResponse.label, "No response");
  assert.deepEqual(noResponse.rows, []);
  assert.equal(noResponse.expected, undefined);
});

test("latest preserves the observed partial or ambiguous result and skips unobserved attempts", () => {
  const { selectLatest } = require(directory + "report-view-model.ts");
  for (const status of ["PARTIAL", "AMBIGUOUS", "NO_RESPONSE"]) {
    const latest = { ...snapshot, status };
    const item = {
      type: "11-10",
      service: 11,
      subtype: 10,
      title: "Schedule detail",
      latest,
    };
    assert.equal(
      selectLatest(item),
      status === "NO_RESPONSE" ? undefined : latest,
    );
  }
});

test("the API scopes history by instance and carries the bounds and cursor unchanged", async () => {
  const { ReportClient } = require(directory + "report-client.ts");
  const urls = [];
  const client = new ReportClient({
    apiUrl: "/yamcs/api",
    doFetch: async (url) => {
      urls.push(new URL(url, "https://yamcs.example"));
      return Response.json({});
    },
  });
  assert.deepEqual(await client.listTypes("my/instance"), []);
  const filters = {
    type: "11-10",
    start: "2026-11-21T06:00:00.000Z",
    stop: "2026-11-23T08:00:00.000Z",
  };
  await client.listReports("my/instance", filters, "opaque+cursor/=");
  assert.equal(
    urls[0].pathname,
    "/yamcs/api/satellite-reports/my%2Finstance/types",
  );
  assert.equal(urls[0].search, "");
  assert.equal(
    urls[1].pathname,
    "/yamcs/api/satellite-reports/my%2Finstance/reports",
  );
  assert.deepEqual(Object.fromEntries(urls[1].searchParams), {
    ...filters,
    next: "opaque+cursor/=",
  });
});

test("history presets use rolling hour bounds and leave custom dates independent", () => {
  const { getPresetHistoryRange } = require(directory + "report-view-model.ts");
  const now = new Date("2026-11-23T02:30:00.123Z");
  for (const [interval, start] of [
    ["PT1H", "2026-11-23T01:30:00.123Z"],
    ["PT6H", "2026-11-22T20:30:00.123Z"],
    ["P1D", "2026-11-22T02:30:00.123Z"],
  ]) {
    assert.deepEqual(getPresetHistoryRange(interval, now), {
      start,
      stop: now.toISOString(),
    });
  }
  assert.deepEqual(getPresetHistoryRange("NO_LIMIT", now), {
    start: undefined,
    stop: undefined,
  });
  assert.equal(getPresetHistoryRange("CUSTOM", now), undefined);
});

test("a saved history link requests a fixed snapshot from its own instance", async () => {
  const { ReportClient } = require(directory + "report-client.ts");
  const urls = [];
  const client = new ReportClient({
    apiUrl: "/api",
    doFetch: async (url) => {
      urls.push(url);
      return Response.json(snapshot);
    },
  });
  assert.deepEqual(await client.getReport("instance-a", snapshot.id), snapshot);
  assert.deepEqual(await client.getReport("instance-b", snapshot.id), snapshot);
  assert.deepEqual(urls, [
    "/api/satellite-reports/instance-a/reports/report-1",
    "/api/satellite-reports/instance-b/reports/report-1",
  ]);
});
