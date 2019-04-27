Get Database Properties
=======================

Get the properties of an open RocksDB database::

    GET /api/archive/:instance/rocksdb/properties/:dbpath*

``dbpath`` is the absolute path of the database on disk.

.. note::

    This operation can be used to debug the inner workings of RocksDB database. For example the property rocksdb.estimate-table-readers-mem will provide an estimation of how much memory is used by the index and filter cache of RocksDB (note that the memory used by RocksDB is outside the java heap space).

.. seealso::

    `<https://github.com/facebook/rocksdb/blob/master/include/rocksdb/db.h>`_
        A description of various RocksDB properties.


.. rubric:: Example

.. code-block:: text

    GET /api/archive/:instance/rocksdb/properties//storage/yamcs-data/yops/ParameterArchive


.. rubric:: Response
.. code-block:: text

    ============== Column Family: data_14380000000========
    rocksdb.num-immutable-mem-table: 0
    rocksdb.num-immutable-mem-table-flushed: 0
    rocksdb.mem-table-flush-pending: 0
    rocksdb.num-running-flushes: 0
    rocksdb.compaction-pending: 0
    rocksdb.num-running-compactions: 0
    rocksdb.background-errors: 0
    rocksdb.cur-size-active-mem-table: 192
    rocksdb.cur-size-all-mem-tables: 192
    rocksdb.size-all-mem-tables: 192
    rocksdb.num-entries-active-mem-table: 0
    rocksdb.num-entries-imm-mem-tables: 0
    rocksdb.num-deletes-active-mem-table: 0
    rocksdb.num-deletes-imm-mem-tables: 0
    rocksdb.estimate-num-keys: 1408482
    rocksdb.estimate-table-readers-mem: 591656
    rocksdb.is-file-deletions-enabled: 0
    rocksdb.num-snapshots: 0
    rocksdb.oldest-snapshot-time: 0
    rocksdb.num-live-versions: 1
    rocksdb.current-super-version-number: 1
    rocksdb.estimate-live-data-size: 254043195
    rocksdb.base-level: 1
    ---------- rocksdb.stats----------------

    ** Compaction Stats [data_14380000000] **
    Level    Files   Size(MB) Score Read(GB)  Rn(GB) Rnp1(GB) Write(GB) Wnew(GB) Moved(GB) W-Amp Rd(MB/s) Wr(MB/s) Comp(sec) Comp(cnt) Avg(sec) KeyIn KeyDrop
    ---------------------------------------------------------------------------------------------------------------------------------------------------------------------
      L0      3/0       2.74   0.8      0.0     0.0      0.0       0.0      0.0       0.0   0.0      0.0      0.0         0         0    0.000       0      0
      L1      4/0     242.27   0.9      0.0     0.0      0.0       0.0      0.0       0.0   0.0      0.0      0.0         0         0    0.000       0      0
     Sum      7/0     245.02   0.0      0.0     0.0      0.0       0.0      0.0       0.0   0.0      0.0      0.0         0         0    0.000       0      0
     Int      0/0       0.00   0.0      0.0     0.0      0.0       0.0      0.0       0.0   0.0      0.0      0.0         0         0    0.000       0      0
    ....


This response contains a dump of various rocksdb properties for each column family. The single value properties are presented in a name: value list. The multiline properties are preceded by a line including the property name between dashes (like the rocksdb.stats in the example above).
