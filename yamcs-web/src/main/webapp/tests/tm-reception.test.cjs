const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
require("@angular/compiler");

// Exercise the real datasource without bootstrapping Angular or adding a test framework.
function loadSource(filename, dependencies) {
  const source = fs.readFileSync(filename, "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      experimentalDecorators: true,
    },
  }).outputText;
  const exports = {};
  vm.runInNewContext(
    compiled,
    {
      exports,
      require: (name) => dependencies[name] ?? require(name),
    },
    { filename },
  );
  return exports;
}

const sourceDirectory = path.resolve(
  __dirname,
  "../projects/webapp/src/app/telemetry/packets/packet-list",
);
const sdkDirectory = path.resolve(__dirname, "../projects/webapp-sdk/src/lib");
const buffer = loadSource(path.join(sourceDirectory, "PacketBuffer.ts"), {});
const { PacketsDataSource } = loadSource(
  path.join(sourceDirectory, "packets.datasource.ts"),
  {
    "@angular/cdk/table": { DataSource: class {} },
    "@yamcs/webapp-sdk": {},
    "./PacketBuffer": buffer,
  },
);

const angularCore = {
  Component: () => (component) => component,
  Inject: () => () => {},
};

test("packet name selection carries every name into the query and URL", () => {
  const { PacketListComponent } = loadSource(
    path.join(sourceDirectory, "packet-list.component.ts"),
    {
      "@angular/core": angularCore,
      "@yamcs/webapp-sdk": { BaseComponent: class {} },
      "../../../shared/hex/hex.component": {},
      "../create-packet-query-dialog/create-packet-query-dialog.component": {},
      "../packets-page-tabs/packets-page-tabs.component": {},
      "./completions": {},
      "./packet-download-link.pipe": {},
      "./packets.datasource": { PacketsDataSource },
    },
  );
  const names = ["/test/first", "/test/second"];
  const controls = {
    name: { value: names },
    filter: { value: null },
    link: { value: "ANY" },
    timeBasis: { value: "GENERATION_TIME" },
    order: { value: "desc" },
  };
  let queryParams;
  const component = Object.assign(
    Object.create(PacketListComponent.prototype),
    {
      filterForm: { controls },
      router: {
        navigate: (_, options) => (queryParams = options.queryParams),
      },
    },
  );
  assert.deepEqual(component.queryOptions().name, names);
  component.updateURL();
  assert.deepEqual(queryParams.name, names);

  controls.name.value = [];
  assert.equal(component.queryOptions().name, undefined);
  component.updateURL();
  assert.equal(queryParams.name, null);
});

test("editing an older single-name packet query can save multiple names", () => {
  const { EditPacketQueryDialogComponent } = loadSource(
    path.join(
      sourceDirectory,
      "../edit-packet-query-dialog/edit-packet-query-dialog.component.ts",
    ),
    {
      "@angular/core": angularCore,
      "@angular/material/dialog": {},
      "@yamcs/webapp-sdk": loadSource(
        path.join(sdkDirectory, "transforms.ts"),
        {},
      ),
    },
  );
  let saved;
  const component = new EditPacketQueryDialogComponent(
    { close() {} },
    {
      instance: "test",
      yamcsClient: {
        async editQuery(_instance, _resource, _queryId, options) {
          saved = options.query;
        },
      },
    },
    {},
    {
      query: { id: "q", name: "Packets", query: { name: "/test/first" } },
    },
  );
  assert.deepEqual(Array.from(component.form.controls.name.value), [
    "/test/first",
  ]);

  const names = ["/test/first", "/test/second"];
  component.form.controls.name.setValue(names);
  component.save();
  assert.deepEqual(saved.name, names);
});

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function datasource(getPackets) {
  return new PacketsDataSource(
    {
      instance: "test",
      yamcsClient: { getPackets },
    },
    { sync: () => ({ unsubscribe() {} }) },
  );
}

test("pagination retains the exact reception-time query, including its stop bound", async () => {
  const calls = [];
  const ds = datasource(async (_, options) => {
    calls.push(options);
    return calls.length === 1
      ? { packets: [{ sequenceNumber: 1 }], continuationToken: "cursor" }
      : { packets: [{ sequenceNumber: 2 }] };
  });
  const options = {
    timeBasis: "RECEPTION_TIME",
    order: "asc",
    start: "2024-01-01T00:00:00Z",
    stop: "2024-01-02T00:00:00Z",
    name: ["a", "b"],
    link: "link",
    filter: "size > 0",
  };
  await ds.loadEntries("realtime", options);
  await ds.loadMoreData();
  for (const [key, value] of Object.entries(options)) {
    assert.deepEqual(calls[1][key], value);
  }
  assert.equal(calls[1].next, "cursor");
  assert.deepEqual(
    Array.from(ds.packets$.value, (p) => p.sequenceNumber),
    [1, 2],
  );
  assert.equal(ds.hasMore(), false);
  ds.disconnect();
});

test("a stale first page cannot replace newer results or continuation state", async () => {
  const old = deferred();
  const ds = datasource((_, options) =>
    options.timeBasis === "GENERATION_TIME"
      ? old.promise
      : Promise.resolve({
          packets: [{ sequenceNumber: 2 }],
          continuationToken: "new",
        }),
  );
  const first = ds.loadEntries("realtime", { timeBasis: "GENERATION_TIME" });
  assert.equal(
    await ds.loadEntries("realtime", { timeBasis: "RECEPTION_TIME" }),
    true,
  );
  old.resolve({ packets: [{ sequenceNumber: 1 }], continuationToken: "old" });
  assert.equal(await first, false);
  assert.equal(ds.packets$.value[0].sequenceNumber, 2);
  assert.equal(ds.continuationToken, "new");
  assert.equal(ds.loading$.value, false);
  ds.disconnect();
});

test("a stale pagination response is discarded after the query changes", async () => {
  const more = deferred();
  let calls = 0;
  const ds = datasource(async () => {
    calls++;
    if (calls === 1)
      return { packets: [{ sequenceNumber: 1 }], continuationToken: "old" };
    if (calls === 2) return more.promise;
    return { packets: [{ sequenceNumber: 3 }] };
  });
  await ds.loadEntries("realtime", { order: "desc" });
  const pending = ds.loadMoreData();
  await ds.loadEntries("realtime", { order: "asc" });
  more.resolve({
    packets: [{ sequenceNumber: 2 }],
    continuationToken: "stale",
  });
  await pending;
  assert.deepEqual(
    Array.from(ds.packets$.value, (p) => p.sequenceNumber),
    [3],
  );
  assert.equal(ds.continuationToken, undefined);
  ds.disconnect();
});

test("a failed page clears loading and a stale failure is ignored", async () => {
  const old = deferred();
  let calls = 0;
  const ds = datasource(() =>
    ++calls === 1 ? old.promise : Promise.resolve({ packets: [] }),
  );
  const pending = ds.loadEntries("realtime", {});
  await ds.loadEntries("realtime", { timeBasis: "RECEPTION_TIME" });
  old.reject(new Error("obsolete"));
  assert.equal(await pending, false);
  assert.equal(ds.loading$.value, false);
  const failed = datasource(() => Promise.reject(new Error("current failure")));
  await assert.rejects(failed.loadEntries("realtime", {}), /current failure/);
  assert.equal(failed.loading$.value, false);
  ds.disconnect();
  failed.disconnect();
});
