Parameters
----------

This table is created by the :doc:`../../services/instance/parameter-recorder` and uses the generation time and sequence number as primary key:

.. code-block:: text

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

* | **gentime**
  | the generation time of the command set by the originator.
* | **ppgroup**
  | a string used to group parameters. The parameters sharing the same group and the same timestamp are stored together.
* | **seqNum**
  | a sequence number supposed to be increasing independently for each group.
* | **rectime**
  | the time when the parameters have been received by Yamcs.

In addition to these columns that are statically created, the pp table will store columns with the name of the parameter and the type ``PROTOBUF(org.yamcs.protobuf.Pvalue$ParameterValue)``.

.. note::
    Because partitioning by ``ppgroup`` is specified, this is also implicitly part of the primary key, but not stored as such in the RocksDB key.
