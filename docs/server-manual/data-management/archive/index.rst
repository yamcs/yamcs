Generic Archive
===============

.. toctree::
    :maxdepth: 1

    telemetry-packets
    events
    command-history
    alarms
    parameters

Yamcs Generic Archive is composed of tables that store data emitted by streams.

Like streams, the tables have a variable number of columns of predefined types. Tables have a primary key composed of one or more columns. The primary key columns are mandatory, a tuple that does not have them will not be stored in the table.

The primary key is used to sort the data. Yamcs uses a (key, value) storage engine (currently RocksDB) for storing the data. Both key and value are byte arrays. Yamcs uses the serialized primary key of the table as the key in RocksDb and the remaining columns serialized as the value.

Although not enforced by Yamcs, it is usual to have the time as part of the primary key.

Yamcs stores time ordered tuples (t, v\ :sub:`1`, v\ :sub:`2`...v\ :sub:`n`) where t is the time and v\ :sub:`1`, v\ :sub:`2`, v\ :sub:`n` are values of various types. The tables are row-oriented and optimized for accessing entire records (e.g. a packet or a group of processed parameters).

Yamcs defines a standard set of tables for storing raw telemetry packets, commands, events, alarms and processed parameters.
