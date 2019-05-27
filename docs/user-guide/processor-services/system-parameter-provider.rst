System Parameter Provider
=========================

Provides parameters received from the ``sys_param`` stream.


Class Name
----------

:javadoc:`org.yamcs.parameter.SystemParametersProvider`


Configuration
-------------

This service is defined in ``etc/processor.yaml``. Example:

.. code-block:: yaml
    :caption: processor.yaml

    realtime:
      services:
        - class: org.yamcs.parameter.SystemParametersProvider
