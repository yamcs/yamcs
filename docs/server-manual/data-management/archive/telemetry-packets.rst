Telemetry Packets
=================

This table is created by the :doc:`../../services/instance/xtce-tm-recorder` and uses the generation time and sequence number as primary key:

.. code-block:: text

    CREATE TABLE tm(
        gentime TIMESTAMP,
        seqNum INT,
        packet BINARY,
        pname ENUM,
        PRIMARY KEY(
            gentime,
            seqNum
        )
     ) HISTOGRAM(pname) PARTITION BY TIME_AND_VALUE(gentime, pname) TABLE_FORMAT=compressed;

Where the columns are:

* | **gentime**
  | generation time of the packet.
* | **seqNum**
  | an increasing sequence number.
* | **packet**
  | the binary packet.
* | **pname**
  | the fully-qualified name XTCE name of the container. In the XTCE container hierarchy, one has to configure which containers are used as partitions. This can be done by setting a flag in the spreadsheet.

If a packet arrives with the same time and sequence number as another packet already in the archive, it is considered duplicate and shall not be stored.

The ``HISTOGRAM(pname)`` clause means that Yamcs will build an overview that can be used to quickly see when data for the given packet name is available in the archive.

The ``PARTITION BY TIME_AND_VALUE`` clause means that data is partitioned in different RocksDB databases and column families based on the time and container name. Currently the time partitioning schema used is ``YYYY/MM`` which implies one RocksDB database per year,month. Inside that database there is one column family for each container that is used for partitioning.

Partitioning the data based on time, ensures that old data is frozen and not disturbed by new data coming in. Partitioning by container has benefits when retrieving data for one specific container for a time interval. If this is not desired, one can set the partitioning flag only on the root container (in fact it is automatically set) so that all packets are stored in the same partition.
