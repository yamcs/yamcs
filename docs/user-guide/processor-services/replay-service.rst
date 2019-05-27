Replay Service
==============

Provides telemetry packets and processed parameters from the archive.


Class Name
----------

:javadoc:`org.yamcs.tctm.ReplayService`


Configuration
-------------

This service is defined in ``etc/processor.yaml``. Example:

.. code-block:: yaml
    :caption: processor.yaml

    Archive:
      services:
        - class: org.yamcs.tctm.ReplayService


Configuration Options
---------------------

excludeParameterGroups (list of string)
    Parameter groups to exclude from being replayed.
