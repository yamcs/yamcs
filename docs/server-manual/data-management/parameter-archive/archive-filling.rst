Archive Filling
===============

There are two fillers that can be used to populate Parameter Archive:

Realtime Filling
    The RealtimeFillerTask will subscribe to a realtime processor and write the parameter values to the archive.

Backfilling
    The ArchiveFillerTask will create from time to time replays from the raw data in the :doc:`../archive/telemetry-packets` and :doc:`../archive/parameters` tables of the Generic Archive.

Due to the fact that data is stored in segments, one segment being a value in the (key, value) RocksDB, it is not efficient to write one row (data corresponding to one timestamp) at a time. It is much more efficient to collect data and write entire or at least partial segments at a time.

The realtime filler will write the partial segments to the archive at each configurable interval. When retrieving data from the Parameter Archive, the latest (near realtime) data will be missing from the archive. That is why Yamcs uses the processor parameter cache to retrieve the near-realtime values.

The backFiller is by default enabled and it can also be used to issue rebuild requests over HTTP. The realtimeFiller has to be enabled in the configuration and the flushInterval (how often to flush the data in the archive) has to be specified. The flushInterval has to be smaller than the duration configured in the parameter cache.

The backFiller is configured with a so called warmupTime (by default 60 seconds) which means that when it performs a replay, it starts the replay earlier by the specified warmupTime amount. The reason is that if there are any algorithms that depend on some parameters in the past for computing the current value, this should give them the chance to warmup. The data generated during the warmup is not stored in the archive (because it is part of the previous segment).
