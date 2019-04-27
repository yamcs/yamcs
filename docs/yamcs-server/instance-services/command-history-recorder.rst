Command History Recorder
========================

Records command history entries. This service stores the data coming from one or more streams into a table ``cmdhist``.


Class Name
----------

:javadoc:`org.yamcs.archive.CommandHistoryRecorder`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.archive.CommandHistoryRecorder

    streamConfig:
      event:
        - cmdhist_realtime
        - cmdhist_dump

With this configuration events emitted to the ``cmdhist_realtime`` or ``cmdhist_dump`` stream are stored into the table ``cmdhist``.
