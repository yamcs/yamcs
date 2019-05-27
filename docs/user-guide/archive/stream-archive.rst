Stream Archive
==============

Yamcs uses streams to transfer tuples of data. A tuple has a variable number of columns, each of predefined type. The Yamcs Stream Archive is composed of tables that store data passing through streams.

Like streams, the tables have a variable number of columns of predefined types. In addition to that, the tables have also a primary key composed of one or more columns. The primary key columns are mandatory, a tuple that does not have them will not be stored in the table.

The primary key is used to sort the data in the table. Yamcs uses a (key,value) storage engine (currently RocksDB) for storing the data. Both key and value are byte arrays. Yamcs uses the serialized primary key of the table as the key in RocksDb and the remaining columns serialized as the value.

Although not enforced by Yamcs, it is usual to have the time as part of the primary key.

Using this Stream Archive, Yamcs defines a standard set of tables. These are `Packet Telemetry`_, `Events`_, `Command History`_, `Alarms`_ and `Parameters`_.


Packet Telemetry
----------------

This table is created by the :doc:`../instance-services/xtce-tm-recorder` and uses the generation time and sequence number as primary key::

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

gentime
    generation time of the packet.
seqNum
    an increasing sequence number.
packet
    the binary packet.
pname
    the fully-qualified name XTCE name of the container. In the XTCE container hierarchy, one has to configure which containers are used as partitions. This can be done by setting a flag in the spreadsheet.

If a packet arrives with the same time and sequence number as another packet already in the archive, it is considered duplicate and shall not be stored.

The ``HISTOGRAM(pname)`` clause means that Yamcs will build an overview that can be used to quickly see when data for the given packet name is available in the archive.

The ``PARTITION BY TIME_AND_VALUE`` clause means that data is partitioned in different RocksDB databases and column families based on the time and container name. Currently the time partitioning schema used is ``YYYY/MM`` which implies one RocksDB database per year,month. Inside that database there is one column family for each container that is used for partitioning.

Partitioning the data based on time, ensures that old data is frozen and not disturbed by new data coming in. Partitioning by container has benefits when retrieving data for one specific container for a time interval. If this is not desired, one can set the partitioning flag only on the root container (in fact it is automatically set) so that all packets are stored in the same partition.


Events
------

This table is created by the :doc:`../instance-services/event-recorder` and uses the generation time, source and sequence number as primary key::

    CREATE TABLE events(
        gentime TIMESTAMP,
        source ENUM,
        seqNum INT,
        body PROTOBUF('org.yamcs.protobuf.Yamcs$Event'),
        PRIMARY KEY(
            gentime,
            source,
            seqNum
        )
    ) HISTOGRAM(source) partition by time(gentime) table_format=compressed;

Where the columns are:

gentime
    the generation time of the command set by the originator.
source
    a string representing the source of the events.
seqNum
    a sequence number provided by the event source. Each source is expected to keep an independent sequence count for the events it generates.


Command History
---------------

This table is created by the :doc:`../instance-services/command-history-recorder` and uses the generation time, origin and sequence number as primary key::

    CREATE TABLE cmdhist(
        gentime TIMESTAMP,
        origin STRING,
        seqNum INT,
        cmdName STRING,
        binary BINARY,
        PRIMARY KEY(
            gentime,
            origin,
            seqNum
        )
    ) HISTOGRAM(cmdName) PARTITION BY TIME(gentime) table_format=compressed;

Where the columns are:

gentime
    the generation time of the command set by the originator.
origin
    a string representing the originator of the command.
seqNum
    a sequence number provided by the originator. Each command originator is supposed to keep an independent sequence count for the commands it sends.
cmdName
    the XTCE fully qualified name of the command.
binary
    the binary packet contents.

In addition to these columns, there will be numerous dynamic columns set by the command verifiers, command releasers, etc.

Recording data into this table is setup with the following statements::

    INSERT_APPEND INTO cmdhist SELECT * FROM cmdhist_realtime;
    INSERT_APPEND INTO cmdhist SELECT * FROM cmdhist_dump;

The INSERT_APPEND says that if a tuple with the new key is received on one of the cmdhist_realtime or cmdhist_dump streams, it will be just inserted into the cmdhist table. If however, a tuple with a key that already exists in the table is received, the columns that are new in the newly received tuple are appended to the already existing columns in the table.


Alarms
------

This table is created by the :doc:`../instance-services/alarm-recorder` and uses the trigger time, parameter name and sequence number as primary key::

    CREATE TABLE alarms(
        triggerTime TIMESTAMP,
        parameter STRING,
        seqNum INT,
        PRIMARY KEY(
            triggerTime,
            parameter,
            seqNum
        )
    ) table_format=compressed;

Where the columns are:

triggerTime
    the time when the alarm has been triggered. Until an alarm is acknowledged, there will not be a new alarm generated for that parameter (even if it were to go back in limits)
parameter
    the fully qualified XTCE name of the parameter for which the alarm has been triggered.
seqNum
    a sequence number increasing with each new triggered alarm. The sequence number will reset to 0 at Yamcs restart.


Parameters
----------

This table is created by the :doc:`../instance-services/parameter-recorder` and uses the generation time and sequence number as primary key::

    CREATE TABLE pp(
        gentime TIMESTAMP,
        ppgroup ENUM,
        seqNum INT,
        rectime TIMESTAMP,
        primary key(
            gentime,
            seqNum
        )
    ) histogram(ppgroup) PARTITION BY TIME_AND_VALUE(gentime,ppgroup) table_format=compressed;

Where the columns are:

gentime
    the generation time of the command set by the originator.
ppgroup
    a string used to group parameters. The parameters sharing the same group and the same timestamp are stored together.
seqNum
    a sequence number supposed to be increasing independently for each group.
rectime
    the time when the parameters have been received by Yamcs.

In addition to these columns that are statically created, the pp table will store columns with the name of the parameter and the type ``PROTOBUF(org.yamcs.protobuf.Pvalue$ParameterValue)``.


.. note::
    Because partitioning by ppgroup is specified, this is also implicitly part of the primary key, but not stored as such in the RocksDB key.
