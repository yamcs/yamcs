Restart Instance
================

Restart an instance::

    POST /api/instances/{instance}:restart

If the instance state is RUNNING, the instance will be stopped and then restarted. Otherwise the instance will be started. Note that the Mission Database will be also reloaded before restart.
