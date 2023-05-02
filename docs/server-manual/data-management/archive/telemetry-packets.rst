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
     ) HISTOGRAM(pname) PARTITION BY VALUE(pname) TABLE_FORMAT=compressed;

Where the columns are:

* | **gentime**
  | generation time of the packet.
* | **seqNum**
  | an increasing sequence number.
* | **packet**
  | the binary packet.
* | **pname**
  | the fully-qualified name name of the container. In a container hierarchy, one has to configure which containers are used as partitions. This can be done by setting a flag in the spreadsheet.

If a packet arrives with the same time and sequence number as another packet already in the archive, it is considered duplicate and shall not be stored.

The ``HISTOGRAM(pname)`` clause means that Yamcs will build an overview that can be used to quickly see when data for the given packet name is available in the archive.

The ``PARTITION BY VALUE`` clause means that data is partitioned in different RocksDB column families based on the container name. This has benefits when retrieving data for one specific container for a time interval. If this is not desired, one can set the partitioning flag only on the root container (in fact it is automatically set) so that all packets are stored in the same partition.
