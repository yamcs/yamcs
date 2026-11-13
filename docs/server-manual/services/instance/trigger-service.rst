Trigger Service
===============

Starts activities automatically when an alarm occurs, or when a condition on parameters becomes true. See :doc:`../../activities/triggers`.


Class Name
----------

:javadoc:`org.yamcs.activities.triggers.TriggerService`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.activities.triggers.TriggerService


Configuration Options
---------------------

enabled (boolean)
    Whether triggers are evaluated when the server starts for the first time. After that, the state set through the API (``triggers:enableAll`` and ``triggers:disableAll``) is remembered across restarts. Default: ``true``

firingRetention (integer)
    Number of days to keep trigger firing records. Use ``0`` to keep them forever. Default: ``0``

triggers (list of maps)
    Triggers declared in the configuration. See :doc:`../../activities/triggers` for the options of each trigger.
