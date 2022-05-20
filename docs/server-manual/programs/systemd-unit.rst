Systemd Unit File
=================

Yamcs package installations include a systemd unit file for starting and stopping Yamcs as a service.

The unit file is located at ``/usr/lib/systemd/system/yamcs.service``.

You should not modify this file directly, but instead use standard systemd mechanisms to customize unit files. See the instructions for your operating system.

Usage::

    systemctl start|stop|restart|status yamcs


systemctl accepts these commands:

.. describe:: start

    Starts Yamcs.

.. describe:: stop

    Stops the Yamcs process and any other processes it may have launched.

.. describe:: restart

    Stops Yamcs if it is running, then starts it again.

.. describe:: status

    Checks if Yamcs is currently running. This will only detect a Yamcs runtime that has been started via systemd.


If you would like Yamcs to start automatically on boot, run::

    systemctl enable yamcs

If you want to revert Yamcs starting automatically, run::

    systemctl disable yamcs
