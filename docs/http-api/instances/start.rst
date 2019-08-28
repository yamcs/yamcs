Start Instance
==============

Start an instance::

    POST /api/instances/{instance}:start

If the instance is in the RUNNING state, this call will do nothing. Otherwise the instance will be started.
