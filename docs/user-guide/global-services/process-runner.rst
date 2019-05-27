Process Process
===============

Runs an external process. If this process goes down, a new process instance is started.


Class Name
----------

:javadoc:`org.yamcs.server.ProcessRunner`


Configuration
-------------

This is a global service defined in ``etc/yamcs.yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.yaml

    services:
    - class: org.yamcs.server.ProcessRunner
        args:
        command: "bin/simulator.sh"


Configuration Options
---------------------

command (string or string[])
    **Required.** Command (and optional arguments) to run.

directory (string)
    Set the working directory of the started subprocess. If unspecified, this defaults to the cwd of Yamcs.

logLevel (string)
    Level at which to log stdout/stderr output. One of ``INFO``, ``DEBUG``, ``TRACE``, ``WARN``, ``ERROR``. Default: ``INFO``

logPrefix (string)
    Prefix to prepend to all logged process output. If unspecified this defaults to '``[COMMAND]``'.
