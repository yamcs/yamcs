yamcsadmin rocksdb bench
------------------------

.. program:: yamcsadmin rocksdb bench

**NAME**

    yamcsadmin rocksdb bench - Benchmark rocksdb storage engine


**SYNOPSIS**

    .. code-block::

        yamcsadmin rocksdb bench [--dbDir DIR] [--baseTime TIME]
            [--count COUNT] [--duration HOURS]


**OPTIONS**

    .. option:: --dbDir DIR

        Directory where the database will be created. A "rocksbench" archive instance will be created in this directory

    .. option:: --baseTime TIME

        Start inserting data with this time. Default: 2017-01-01T00:00:00

    .. option:: --count COUNT

        The partition counts for the 5 frequencies: [10/sec, 1/sec, 1/10sec, 1/60sec and 1/hour]. It has to be specified as a string (use     quotes).

    .. option:: --duration HOURS

        The duration in hours of the simulated data. Default: 24


**DESCRIPTION**

    The benchmark consists of a table load and a few selects. The table is loaded with telemetry packets received at frequencies of [10/sec, 1/sec, 1/10sec, 1/60sec and 1/hour]. The table will be identical to the tm table and will contain a histogram on pname (= packet name). It is possible to specify how many partitions (i.e. how many different pnames) to be loaded for each frequency and the time duration of the data.
