Post-installation Setup
=======================

After you have installed Yamcs, you are ready to initialize a Yamcs configuration. This includes:

* Setting up a data directory
* Setting up a configuration directory

Depending on your chosen installation method, it may be that you can skip this section. In particular, rpm distributions apply conventions by using a pre-defined data directory and set of configuration files.


Starting Yamcs Server
---------------------

Normally Yamcs Server should be configured to start automatically on boot via ``/etc/init.d/yamcs-server``. The command will automatically run itself as a lower privilege user (username ``yamcs``), but must initially be run as root for this to happen. Yamcs Server can be started and stopped as a service via commands such as ``service yamcs-server start`` and ``service yamcs-server stop``. These commands use the init.d script and will run Yamcs as the appropriate user. It is also possible to directly use the script ``/opt/yamcs/bin/yamcsd``, but use of the ``service`` command is preferred.
