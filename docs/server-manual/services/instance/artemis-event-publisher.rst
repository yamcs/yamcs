Artemis Event Publisher
=======================

Publish ``event`` stream data to an Artemis broker.


Class Name
----------

:javadoc:`org.yamcs.artemis.ArtemisEventService`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.artemis.ArtemisEventService
        args:
          streamNames:
            - events_realtime
            - events_dump


Configuration Options
---------------------

streamNames (list of strings)
    The streams to publish. For each stream the target address is composed as `instance.stream`.


By default, messages are published to an embedded broker (in-VM). This assumes that :doc:`Artemis Server <../global/artemis-server>` was configured as a global service. In order to use an external broker you can configure the property ``artemisUrl`` in either ``etc/yamcs.(instance).yaml`` or ``etc/yamcs.yaml``:

.. code-block:: yaml

    artemisUrl: tcp://remote-host1:5445

If defined, the instance-specific configuration is selected over the global configuration. The URL format follows Artemis conventions and is not further detailed in this manual.
