Alarm Reporter
==============

Generates events for changes in the alarm state of any parameter on the specific processor. Note that this is independent from the actual alarm checking.


Class Name
----------

:javadoc:`org.yamcs.alarms.AlarmReporter`


Configuration
-------------

This service is defined in ``etc/processor.yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: processor.yaml

    realtime:
      services:
        - class: org.yamcs.alarms.AlarmReporter


Configuration Options
---------------------

source (string)
    The source name of the generated events. Default: ``AlarmChecker``
