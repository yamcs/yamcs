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
    **Required.** The directory where the data will be read from.

deleteAfterImport (boolean)
    Remove the file after importing all the data. By default set to true, can be set to false to import the same data again and again.

delayBetweenPackets (integer)
    When importing a file, wait this many milliseconds after each packet. This option together with the previous one can be used to simulate incoming realtime data.

packetPreprocessorClassName (string)
    Class name of a :javadoc:`~org.yamcs.tctm.PacketPreprocessor` implementation. Default is :javadoc:`org.yamcs.tctm.IssPacketPreprocessor` which applies :abbr:`ISS (International Space Station)` conventions.

packetPreprocessorArgs (map)
    Optional args of arbitrary complexity to pass to the PacketPreprocessor. Each PacketPreprocessor may support different options.

lastPacketStream (string)
    Optional stream name. If specified, the last packet in an imported file, is emitted to this stream, in addition to the regular stream defined with the ``stream`` option.

    The intended use case, is to have ``stream: tm_dump`` and ``lastPacketStream: tm_realtime``. Then most data goes directly into the Archive, while only the last packet's data goes to realtime clients.
