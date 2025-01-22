Alarm Mirroring
===============

Mirrors alarms. Works in conjunction with the :ref:`replication slave <replication-slave>` to mirror alarms from a replication master.

It works by monitoring the streams of type parameterAlarm and eventAlarm (usually these are `alarms_realtime`` and `event_alarms_realtime`` respectively). These streams have to be configured for replication. Since information on these streams is only sent when an alarm is created or updated, the service maintains its own database of alarms. At startup, it loads alarms triggered within the last 30 days.

Please see the replication1 example on how this service is configured to mirror alarms from node1 to node2. Note in the processor.yaml that node2 uses a processor without the usual alarm servers configured.


Class Name
----------

:javadoc:`org.yamcs.alarms.AlarmMirrorService`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.alarms.AlarmMirrorService
        args: 
            alarmLoadDays: 30


Configuration Options
---------------------

alarmLoadDays (float)
    Specifies the number of days' worth of alarms to load at startup. This parameter determines the time range based on the alarm's trigger time (i.e., the moment the alarm was triggered).
    Setting a negative value, disables loading alarms from the database.
    
    Default: 30