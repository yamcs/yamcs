Alarm Recorder
==============

Records alarms. This service stores the data coming from one or more streams into a table ``alarms``.


Class Name
----------

:javadoc:`org.yamcs.archive.AlarmRecorder`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.archive.AlarmRecorder

    streamConfig:
      alarm:
        - alarms_realtime

With this configuration alarms emitted to the ``alarms_realtime`` stream are stored into the table ``alarms``.
