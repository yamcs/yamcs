Events
======

This table is created by the :doc:`../../services/instance/event-recorder` and uses the generation time, source and sequence number as primary key:

.. code-block:: text

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

* | **gentime**
  | the generation time of the command set by the originator.
* | **source**
  | a string representing the source of the events.
* | **seqNum**
  | a sequence number provided by the event source. Each source is expected to keep an independent sequence count for the events it generates.
