TSE Data Link
=============

Sends telecommands to a configured `../services/global/tse-commander` and reads back output as processed parameters.


Class Name
----------

:javadoc:`org.yamcs.tse.TseDataLink`


Configuration Options
---------------------

host (string)
    **Required.** The host of the TSE Commander.

port (integer)
    **Required.** The TCP port of the TSE Commander.

tcStream (string)
    Stream where command instructions are received. Default: ``tc_tse``.

ppStream (string)
    Stream where to emit received parameters. Default: ``pp_tse``.
