Stream Archive
==============

.. toctree::

    packet-telemetry
    events
    command-history
    alarms
    parameters

Yamcs uses streams to transfer tuples of data. A tuple has a variable number of columns, each of predefined type. The Yamcs Stream Archive is composed of tables that store data passing through streams.

Like streams, the tables have a variable number of columns of predefined types. In addition to that, the tables have also a primary key composed of one or more columns. The primary key columns are mandatory, a tuple that does not have them will not be stored in the table.

The primary key is used to sort the data in the table. Yamcs uses a (key, value) storage engine (currently RocksDB) for storing the data. Both key and value are byte arrays. Yamcs uses the serialized primary key of the table as the key in RocksDb and the remaining columns serialized as the value.

Although not enforced by Yamcs, it is usual to have the time as part of the primary key.

Using this Stream Archive, Yamcs defines a standard set of tables.
