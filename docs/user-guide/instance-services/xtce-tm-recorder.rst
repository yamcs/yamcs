XTCE TM Recorder
================

Records XTCE TM sequence containers. This service stores the data coming from one or more streams into a table ``tm``.


Class Name
----------

:javadoc:`org.yamcs.archive.XtceTmRecorder`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.archive.XtceTmRecorder

    streamConfig:
      tm:
        - tm_realtime
        - tm_dump

With this configuration containers coming from both the tm_realtime and tm_dump streams are stored into the table ``tm``.


Configuration Options
---------------------

streams (list of strings)
    The streams to record. When unspecified, all ``tm`` streams defined in ``streamConfig`` are recorded.
