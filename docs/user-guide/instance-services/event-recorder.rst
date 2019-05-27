Event Recorder
==============

Records events. This service stores the data coming from one or more streams into a table ``events``.


Class Name
----------

:javadoc:`org.yamcs.archive.EventRecorder`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.archive.EventRecorder

    streamConfig:
      event:
        - events_realtime
        - events_dump

With this configuration events emitted to the ``events_realtime`` or ``events_dump`` stream are stored into the table ``events``.
