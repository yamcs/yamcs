Alarms
======

This table is created by the :doc:`../../services/instance/alarm-recorder` and uses the trigger time, parameter name and sequence number as primary key:

.. code-block:: text

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

* | **triggerTime**
  | the time when the alarm has been triggered. Until an alarm is acknowledged, there will not be a new alarm generated for that parameter (even if it were to go back in limits)
* | **parameter**
  | the fully qualified name of the parameter for which the alarm has been triggered.
* | **seqNum**
  | a sequence number increasing with each new triggered alarm. The sequence number will reset to 0 at Yamcs restart.
