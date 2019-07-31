Replay Server
=============

This service handles replay requests of archived data. Each replay runs with a separate processor that runs in parallel to the realtime processing.


Class Name
----------

:javadoc:`org.yamcs.archive.ReplayServer`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.archive.ReplayServer
