Stop Instance
==============

Stop an instance::

    POST /api/instances/{instance}:start

Stop all services of the instance. The instance state will be OFFLINE. If the instance state is already OFFLINE, this call will do nothing.
