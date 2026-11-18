# TM reception-time index

The `tm` reception index provides ordered seeks by `(rectime, pname enum ID, gentime, seqNum)`. Packet identity, deduplication and generation-time partitioning retain their existing semantics. Packet listing and bulk export accept `timeBasis=GENERATION_TIME|RECEPTION_TIME` and `order=asc|desc`. Omitted basis means generation time; omitted direction means descending for listing and ascending for export.

Enable `receptionTimeIndex: true` in the `args` of `XtceTmRecorder`. Provisioning adds an index to existing tables and starts background backfill before recording begins. Turning the provisioning flag off does not remove an installed index or stop its maintenance. The explicit rebuild endpoint also provisions an absent index. A conflicting secondary index or a custom incompatible TM schema is rejected.

## Storage and lifecycle

- Index keys and row pointers live in the main database's default column family. Packet payloads remain in the original `rt_data` partitions, without an extra copy in the main DB.
- Same-database row/index changes use one batch. Cross-database mutations commit a row and uniquely identified pending operation in `_secondary_index_pending`, synchronize the source WAL, apply and synchronize the main index, and then acknowledge the journal operation. Pending operations are replayed before a newer operation on the same row, preserving order and making replay idempotent.
- Index metadata tracks `BUILDING`, `READY`, `FAILED`, revision, row counts and source-partition checkpoints. `ABSENT` is represented by missing index metadata. Legacy unmanaged indexes retain their existing ready default.
- Historical backfill rereads each source row under the same lock as recording and writes only index entries. Index changes and progress checkpoints share the main WAL; a synchronized checkpoint cannot advance beyond its index writes. Incomplete batches are repeated after restart.
- Fresh rebuilds stop admission of reception scans, drain active reception readers in the background, increment the revision, clear under the lifecycle barrier, and rebuild. Generation-time browsing remains available. A failed maintenance operation gates reception reads until recovery/rebuild.
- Pagination tokens bind the encoded key, direction, revision and original query. Every page retains its start/stop window. There is no snapshot shared across HTTP pages; concurrently inserted packets behind a passed cursor may require refreshing the page, as with ordinary keyset pagination.
- Missing reception times are counted during historical backfill and excluded from reception browsing. Generation queries can still access those rows, subject to existing packet conversion behavior.

## Task-specific verification

Run from the repository root, with RTK:

```sh
rtk mvn -pl yamcs-core -am -Dtest=TmReceptionIndexTest,TmPacketArchiveQueryTest,PacketsReceptionPermissionsTest,SecondaryIndexTest,SecondaryIndex2Test,SecondaryIndex3Test -Dsurefire.failIfNoSpecifiedTests=false test
rtk mvn -pl tests -am -Dtest=PacketsReceptionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false package
rtk mvn -pl yamcs-core -am -Dtest=TmReceptionPerformanceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

In `yamcs-web/src/main/webapp`:

```sh
rtk npm run test:tm-reception
rtk npm run build
```

These commands select only feature-related tests. The web datasource tests use Node's test runner and transpile the real TypeScript source with minimal Angular stubs. The production SDK/webapp build separately checks templates and types.

## Local benchmark and rollout constraint

The small benchmark uses 4,000 packets, 21 generation-time/name partitions and 100-packet pages. The final recorded local run measured generation first-page median 0.130 ms, reception median 0.297 ms, and reception later-page latency 0.280 ms. Both reception pages read 101 index entries and 101 payloads. A selective name/link filter inspected 2,824 candidates; link/search filters are not independently indexed. Logical index keys and values occupied 156,000 bytes, excluding RocksDB overhead and journal metadata.

The same run measured approximately 13,477 rows/s without indexing, 137 rows/s with synchronous cross-database journal maintenance, and 23,675 historical rows/s during backfill. Restarting and recovering 100 unapplied journal operations measured approximately 172 operations/s, including reopening the archive and publishing readiness. These synthetic warm-cache numbers are machine-specific and are not production capacity estimates. The ingestion regression is a material deployment constraint. Keep provisioning disabled until representative throughput, archive size and storage hardware have been measured; a future group-commit design may be needed for higher-rate archives. No production archive was migrated by these tests.

The browser smoke check used a separate synthetic test instance. It verified generation versus reception windows, both order directions, URL restoration, a custom reception-time range, a raw export containing the expected packet byte, and the disabled reception-time option on an instance without an index. The packet filter controls wrap at narrow widths.

Roll out to one instance, wait for `READY`, compare packet sets and export ordering, and measure first/later pages, filtered scans, ingestion and index storage before expanding. Product rollback means choosing generation-time browsing while keeping a compatible backend maintaining the index; disabling provisioning does not authorize deleting the index or downgrading its storage code.
