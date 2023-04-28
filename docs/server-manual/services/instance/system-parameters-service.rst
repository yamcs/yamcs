System Parameters Service
===========================

Collects system parameters from any Yamcs component at a frequency of 1 Hz. Parameter values are emitted to the ``sys_var`` stream.


Class Name
----------

:javadoc:`org.yamcs.parameter.SystemParametersService`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.parameter.SystemParametersService
        args:
          provideJvmVariables: true


Configuration Options
---------------------

provideJvmVariables (boolean)
    When set to ``true`` this service will create a few system parameters that allows monitoring basic JVM properties such as memory usage and thread count. Default: ``false``
