const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");

const directory = path.resolve(
  __dirname,
  "../projects/webapp/src/app/events/event-list",
);
function load(name, dependencies = {}) {
  const source = fs.readFileSync(path.join(directory, name), "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  const exports = {};
  vm.runInNewContext(compiled, {
    exports,
    require: (id) => dependencies[id] ?? require(id),
  });
  return exports;
}
const { EventBuffer } = load("EventBuffer.ts");
const { EventsDataSource } = load("events.datasource.ts", {
  "@angular/cdk/table": { DataSource: class {} },
  "@yamcs/webapp-sdk": {},
  "./EventBuffer": { EventBuffer },
});
function event(seqNumber, subsystem) {
  return {
    generationTime: `2026-01-01T00:00:0${seqNumber}Z`,
    source: "APID_1",
    seqNumber,
    extra: subsystem === undefined ? undefined : { subsystem },
  };
}

test("extra sorting combines archive and realtime, with missing values last", () => {
  const buffer = new EventBuffer(() => {});
  buffer.addArchiveData([event(1, "PAYLOAD"), event(2), event(3, "FSW")]);
  buffer.addRealtimeEvent(event(4, "FSW"));
  assert.deepEqual(
    Array.from(buffer.snapshot("subsystem", "asc"), (e) => e.seqNumber),
    [4, 3, 1, 2],
  );
  assert.deepEqual(
    Array.from(buffer.snapshot("subsystem", "desc"), (e) => e.seqNumber),
    [1, 4, 3, 2],
  );
  assert.deepEqual(
    Array.from(buffer.snapshot(), (e) => e.seqNumber),
    [4, 3, 2, 1],
  );
  buffer.compact(2);
  assert.deepEqual(
    Array.from(buffer.snapshot("subsystem", "asc"), (e) => e.seqNumber),
    [4, 3],
  );
});

test("configured extra fields receive include and exclude filter completions", () => {
  const { eventCompletions } = load("completions.ts", {
    "@codemirror/autocomplete": {},
    codemirror: {},
  });
  const labels = Array.from(
    eventCompletions([{ id: "subsystem", label: "Subsystem" }]),
    (c) => c.label,
  );
  assert.ok(labels.includes("extra.subsystem"));
  assert.ok(labels.includes("-extra.subsystem"));
  assert.ok(labels.includes("source"));
});

test("datasource retains extra filters and sorts newly loaded and streamed events", async () => {
  const requests = [];
  let receive;
  let tick;
  const ds = new EventsDataSource(
    {
      instance: "test",
      yamcsClient: {
        getEventSources: async () => [],
        getEvents: async (_, options) => {
          requests.push(options);
          return [event(1, "PAYLOAD"), event(2, "FSW")];
        },
        createEventSubscription: (options, callback) => {
          requests.push(options);
          receive = callback;
          return {
            cancel() {},
            sendMessage: (options) => requests.push(options),
          };
        },
      },
    },
    {
      sync: (callback) => {
        tick = callback;
        return { unsubscribe() {} };
      },
    },
  );
  ds.sortExtra("extra.subsystem", "asc");
  await ds.loadEvents({ filter: "extra.subsystem = FSW" });
  assert.deepEqual(
    Array.from(ds.events$.value, (e) => e.seqNumber),
    [2, 1],
  );
  ds.startStreaming();
  receive(event(3, "AAA"));
  tick();
  assert.deepEqual(
    Array.from(ds.events$.value, (e) => e.seqNumber),
    [3, 2, 1],
  );
  assert.equal(requests[0].filter, "extra.subsystem = FSW");
  assert.equal(requests[1].filter, "extra.subsystem = FSW");
  ds.sortExtra("extra.subsystem", "");
  assert.deepEqual(
    Array.from(ds.events$.value, (e) => e.seqNumber),
    [3, 2, 1],
  );
  ds.disconnect();
});
