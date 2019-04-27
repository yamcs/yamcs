File Polling TM Data Link
=========================

Reads data from files in a directory, importing it into the configured stream. The directory is polled regularly for new files and the files are imported one by one. After the import, the file is removed.

Class Name
----------

:javadoc:`org.yamcs.tctm.FilePollingTmDataLink`


Configuration Options
---------------------

stream (string)
    **Required.** The stream where data is emitted

incomingDir (string)
    The directory where the data will be read from. If not specified, the data will be read from ``<yamcs-incoming-dir>/<instance>/tm/`` where ``yamcs-incoming-dir`` is the value of the incomingDir property in ``etc/yamcs.yaml``.

deleteAfterImport (boolean)
    Remove the file after importing all the data. By default set to true, can be set to false to import the same data again and again.

delayBetweenPackets (integer)
    When importing a file, wait this many milliseconds after each packet. This option together with the previous one can be used to simulate incoming realtime data.

packetPreprocessorClassName (string)
    Class name of a :javadoc:`~org.yamcs.tctm.PacketPreprocessor` implementation. Default is :javadoc:`org.yamcs.tctm.IssPacketPreprocessor` which applies ISS conventions.

packetPreprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the PacketPreprocessor. Each PacketPreprocessor may support different options.
