Parameter Archive
=================

The Parameter Archive stores for each parameter tuples of (t\ :sub:`i`, ev\ :sub:`i`, rv\ :sub:`i`, ps\ :sub:`i`) where:

t\ :sub:`i`
    the *generation* timestamp of the value. The *reception* timestamp is not stored in the Parameter Archive.
ev\ :sub:`i`
    the engineering value of the parameter at the given time.
rv\ :sub:`i`
    the engineering value of the parameter at the given time.
ps\ :sub:`i`
    the parameter status of the parameter at the given time.

The parameter status includes attributes such as out-of-limits indicators (alarms) and processing status. XTCE provides a mechanism through which a parameter can change its alarm ranges depending on the context. For this reason the Parameter Archive also stores the parameter status and the applicable alarm ranges at the given time.

In order to speed up the retrieval, the Parameter Archive stores data in segments of approximately 70 minutes. That means that all engineering values for one parameter for the 70 minutes are stored together; same for raw values, parameter status and timestamps. More detail about the Parameter Archive organization is documented under :doc:`parameter-archive-internals`.

Having all the data inside one segment of the same type offers possibility for good compression especially if the values do not change much or at all (as it is often the case).

While this structure is good for fast retrieval, it does not allow updating data very efficiently and in any case not in realtime (like the :doc:`stream-archive` does). This is why the Parameter Archive is filled in batch mode - data is accumulated in memory and flushed to disk periodically. The sections below explain the different filling strategies implemented.


Archive Filling
---------------

There are two fillers that can be used to populate Parameter Archive:

Realtime filling
    The RealtimeFillerTask will subscribe to a realtime processor and write the parameter values to the archive.

Backfilling
    The ArchiveFillerTask will create from time to time replays from the :doc:`stream-archive` and write the generated parameters to the archive.

Due to the fact that data is stored in segments, one segment being a value in the (key,value) RocksDB, it is not efficient to write one row (data corresponding to one timestamp) at a time. It is much more efficient to collect data and write entire or at least partial segments at a time.

The realtime filler will write the partial segments to the archive at each configurable interval. When retrieving data from the Parameter Archive, the latest (near realtime) data will be missing from the archive. That is why Yamcs uses the processor parameter cache to retrieve the near-realtime values.

The backFiller is by default enabled and it can also be used to issue rebuild requests over HTTP. The realtimeFiller has to be enabled in the configuration and the flushInterval (how often to flush the data in the archive) has to be specified. The flushInterval has to be smaller than the duration configured in the parameter cache.

The backFiller is configured with a so called warmupTime (by default 60 seconds) which means that when it performs a replay, it starts the replay earlier by the specified warmupTime amount. The reason is tha if there are any algorithms that depend on some parameters in the past for computing the current value, this should give them the chance to warmup. The data generated during the warmup is not stored in the archive (because it is part of the previous segment).
