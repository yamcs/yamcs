Logging
=======

Yamcs allows capturing runtime log messages at different verbosity levels to different output handlers.

By default, if unconfigured, Yamcs will emit messages at INFO level to stdout.

The ``yamcsd`` program accepts some options to modify these defaults. In particular:

``--log``
    The numeric verbosity level, where 0 = OFF, 1 = WARNING, 2 = INFO, 3 = FINE and 4 = ALL. Default: 2

``--log-config``
    Detailed logger verbosity levels. If unspecified, the ``--log`` option impacts all loggers, which may lead to excessive output.

``--no-color``
    Turn off ANSI color codes


If the configuration directory of Yamcs includes a file :file:`etc/logging.properties`, then logging properties are read from this file instead of applying the default console logging. Logging-related program arguments (e.g. verbosity) are then ignored.

The :file:`etc/logging.properties` uses the standard Java logging format, which allows to tweak the logging in much more detail than what is possible through the command-line flags of the yamcsd executable.

A full description of the syntax is beyond the scope of this manual, but see this example of how we currently configure our generic RPM packages:

.. code-block:: text
    :caption: logging.properties

    handlers = java.util.logging.ConsoleHandler, java.util.logging.FileHandler
    
    java.util.logging.ConsoleHandler.level = INFO
    java.util.logging.ConsoleHandler.formatter = org.yamcs.logging.JournalFormatter
    java.util.logging.ConsoleHandler.filter = org.yamcs.logging.GlobalFilter
    
    java.util.logging.FileHandler.level = ALL
    java.util.logging.FileHandler.pattern = /opt/yamcs/log/yamcs-server.log
    java.util.logging.FileHandler.limit = 20000000
    java.util.logging.FileHandler.count = 50
    java.util.logging.FileHandler.formatter = org.yamcs.logging.CompactFormatter
    
    org.yamcs.level = FINE


There are two handlers:

#. A ConsoleHandler prints its messages to stdout. The console output can for example be consumed by an init system like systemd. This configuration uses a JournalFormatter that prints short messages without timestamp for direct injection into the systemd journal, it also applies a GlobalFilter that will remove log messages specific to an instance. This makes Yamcs less chatty.

#. A FileHandler defines the properties used for logging to :file:`/opt/yamcs/log/yamcs-server.log.x`. The FileHandler in this configuration applies a rotation 20 MB with a maximum of 50 files. The theoretic maximum of disk space is therefore 1 GB. The most recent log file can be found at :file:`/opt/yamcs/log/yamcs-server.log.0`. Note that when Yamcs Server is restarted the log files will always rotate even if :file:`yamcs-server.log.0` had not yet reached 20 MB.

This configuration logs messages coming from ``org.yamcs`` loggers at maximum FINE level. Each handler may apply a further level restriction. This is applied after the former level restriction. For example the above FileHandler has level ALL, however it will never print messages more verbose than FINE.

For specific use cases, Yamcs includes a few custom loggers:

.. rubric:: ``org.yamcs.logging.SyslogHandler``

Writes to syslogd over UDP with messages formatted according to RFC 3164 (BSD syslog).

The formatting of this handler cannot be modified, and does not include full stacktrace information.

``org.yamcs.logging.SyslogHandler.level``
   Minimum level of loggable messages. Default: ALL.

``org.yamcs.logging.SyslogHandler.host``
    Syslog host. Defaults to loopback.

``org.yamcs.logging.SyslogHandler.port``
    Syslog port. Default: 514.

``org.yamcs.logging.SyslogHandler.facility``
    Syslog facility. Default: 1 (= user-level messages)


.. rubric:: ``org.yamcs.logging.WatchedFileHandler``

Handler that watches the file that it is logging to. When that file is deleted, the handler will close and reopen a new file with the same name. This handler is designed to be used with programs like ``logrotate`` that take care of log rotation outside of the :abbr:`JVM (Java Virtual Machine)`. Without the watch functionality, log messages would just continue to be written to the old (rotated) file.

``org.yamcs.logging.WatchedFileHandler.level``
      Minimum level of loggable messages. Default: ALL.

``org.yamcs.logging.WatchedFileHandler.filename``
      Name of the file that is logged to. Default: yamcs.log

``org.yamcs.logging.WatchedFileHandler.filter``
     Instance of ``java.util.logging.Filter``. Default: unfiltered

``org.yamcs.logging.WatchedFileHandler.formatter``
     Instance of ``java.util.logging.Formatter``. Default: ``java.util.logging.XMLFormatter``
