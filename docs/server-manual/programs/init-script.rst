yamcs-server init script
========================

Yamcs package installations include an init script for starting and stopping Yamcs in System V-style.

This script is located at ``/etc/init.d/yamcs-server`` and should not be run directly but instead via your system's service manager. This will perform proper stepdown to the ``yamcs`` user.

Usage::

    service yamcs-server start|stop|restart|status

Or alternatively::

    systemctl start|stop|restart|status yamcs-server

.. warning::

    It is not recommended to use this init script on systems that run systemd. Instead use the native systemd unit file. See :doc:`systemd-unit`.


The init script accepts these commands:

.. describe:: start

    Starts Yamcs and stores the PID of the yamcsd process to ``/var/run/yamcs-server.pid``.

.. describe:: stop

    Stops the Yamcs process based on the PID found in ``/var/run/yamcs-server.pid``.

.. describe:: restart

    Stops Yamcs if it is running, then starts it again.

.. describe:: status

    Checks if Yamcs is currently running. This does a PID check and will not detect a Yamcs runtime that has been started on the system without use of this init script.
