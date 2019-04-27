Artemis Parameter Publisher
===========================

Publish ``param`` stream data to an Artemis broker.


Class Name
----------

:javadoc:`org.yamcs.artemis.ArtemisPpService`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.artemis.ArtemisPpService
        args: [pp_realtime, pp_dump, sys_param]

``args`` must be a an array of strings indicating which streams to publish. For each stream the target address is composed as `instance.stream`. In the example tuples from the streams ``pp_realtime``, ``pp_dump`` and ``pp_dump`` are published to the addresses ``simulator.pp_realtime``, ``simulator.pp_dump`` and ``simulator.pp_sys_param`` respectively.

By default, messages are published to an embedded broker (in-VM). This assumes that :doc:`Artemis Server <../global-services/artemis-server>` was configured as a global service. In order to use an external broker you can configure the property ``artemisUrl`` in either ``etc/yamcs.(instance).yaml`` or ``etc/yamcs.yaml``:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    artemisUrl: tcp://remote-host1:5445

.. code-block:: yaml
    :caption: yamcs.yaml

    artemisUrl: tcp://remote-host1:5445

If defined, the instance-specific configuration is selected over the global configuration. The URL format follows Artemis conventions and is not further detailed in this manual.
