yamcs-server init script
========================

Yamcs distributions include an init script for starting and stopping Yamcs Server in System V-style.

This script is located at ``/etc/init.d/yamcs-server`` and should not be run directly but instead via your system's service manager. This will perform proper stepdown to the ``yamcs`` user.

For example::

    systemd start|stop|restart|status yamcs-server

Or alternatively::

    service yamcs-server start|stop|restart|status


The init script accepts these commands:

.. describe:: start

    Starts Yamcs Server and stores the PID of the yamcsd process to ``/var/run/yamcs-server.pid``.

.. describe:: stop

    Stops the Yamcs Server process based on the PID found in ``/var/run/yamcs-server.pid``.

.. describe:: restart

    Stops Yamcs Server if it is running, then starts it again.

.. describe:: status

    Checks if Yamcs Server is currently running. This does a PID check and will not detect a Yamcs Server runtime that has been started on the system without use of this init script.
