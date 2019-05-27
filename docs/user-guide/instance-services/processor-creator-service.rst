Processor Creator Service
=========================

Creates persistent processors owned by the system user.


Class Name
----------

:javadoc:`org.yamcs.ProcessorCreatorService`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.ProcessorCreatorService
        args:
          name: realtime
          type: realtime


Configuration Options
---------------------

name (string)
    **Required.** The name of the processor

type (string)
    **Required.** The type of the processor

config (string)
    Configuration string to pass to the processor
