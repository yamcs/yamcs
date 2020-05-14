Command History
===============

This table is created by the :doc:`../../services/instance/command-history-recorder` and uses the generation time, origin and sequence number as primary key:

.. code-block:: text

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

* | **gentime**
  | the generation time of the command set by the originator.
* | **origin**
  | a string representing the originator of the command.
* | **seqNum**
  | a sequence number provided by the originator. Each command originator is supposed to keep an independent sequence count for the commands it sends.
* | **cmdName**
  | the fully qualified name of the command.
* | **binary**
  | the binary packet contents.

In addition to these columns, there will be numerous dynamic columns set by the command verifiers, command releasers, etc.

Recording data into this table is setup with the following statements:

.. code-block:: text

    INSERT_APPEND INTO cmdhist SELECT * FROM cmdhist_realtime;
    INSERT_APPEND INTO cmdhist SELECT * FROM cmdhist_dump;

The ``INSERT_APPEND`` clause says that if a tuple with the new key is received on one of the cmdhist_realtime or cmdhist_dump streams, it will be just inserted into the ``cmdhist`` table. If however, a tuple with a key that already exists in the table is received, the columns that are new in the newly received tuple are appended to the already existing columns in the table.
