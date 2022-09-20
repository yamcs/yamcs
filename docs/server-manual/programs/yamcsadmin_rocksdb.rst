yamcsadmin rocksdb
==================

.. program:: yamcsadmin rocksdb

Synopsis
--------

.. rst-class: synopsis

    | **yamcsadmin rocksdb** compact [--dbDir <*DIR*>] [--sizeMB <*SIZE*>]
    | **yamcsadmin rocksdb** bench [--dbDir DIR] [--baseTime TIME]
                             [--count COUNT] [--duration HOURS]


Description
-----------

Provides low-level RocksDB data operations.


Commands
--------

.. describe:: compact [--dbDir DIR] [--sizeMB SIZE]

    Compact RocksDB database

.. describe:: bench [--dbDir DIR] [--baseTime TIME] [--count COUNT] [--duration HOURS]

    Benchmark RocksDB storage engine.

    A ``rocksbench`` archive instance will be created in the directory indicated by :option:`--dbDir`.

    The benchmark consists of a table load and a few selects. The table is loaded with telemetry packets received at frequencies of [10/sec, 1/sec, 1/10sec, 1/60sec and 1/hour]. The table will be identical to the tm table and will contain a histogram on pname (= packet name). It is possible to specify how many partitions (i.e. how many different pnames) to be loaded for each frequency and the time duration of the data.


Options
-------

.. option:: --dbDir <DIR>

    Database directory.

.. option:: --sizeMB <SIZE>

    This option is only valid for the ``compact`` command.

    Target size of each SST file in MB (default is 256 MB).

.. option:: --baseTime <TIME>

    This option is only valid for the ``bench`` command.

    Start inserting data with this time. Default: 2017-01-01T00:00:00

.. option:: --count <COUNT>

    This option is only valid for the ``bench`` command.

    The partition counts for the 5 frequencies: [10/sec, 1/sec, 1/10sec, 1/60sec and 1/hour]. It has to be specified as a string (use quotes).

.. option:: --duration <HOURS>

    This option is only valid for the ``bench`` command.

    The duration in hours of the simulated data. Default: 24
