Stop Service
=============

Stop a global service::

    POST /api/services/_global/{name}:start

Stop a service for a specific Yamcs instance::

    POST /api/services/{instance}/{name}:stop


.. note::

    Once stopped, a service cannot be resumed. Instead a new service instance will be created and started.
