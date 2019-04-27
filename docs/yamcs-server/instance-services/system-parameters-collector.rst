System Parameters Collector
===========================

Collects system parameters from any Yamcs component at a frequency of 1Hz. Parameter values are emitted to the ``sys_var`` stream.


Class Name
----------

:javadoc:`org.yamcs.parameter.SystemParametersCollector`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.parameter.SystemParametersCollector
        args:
          provideJvmVariables: true


Configuration Options
---------------------

provideJvmVariables (boolean)
    When set to ``true`` this service will create a few system parameters that allows monitoring basic JVM properties such as memory usage and thread count. Default: ``false``
