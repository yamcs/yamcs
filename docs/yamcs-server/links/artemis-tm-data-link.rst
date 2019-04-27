Artemis TM Data Link
====================

Reads ``tm`` data from an Artemis queue and publishes it to the configured stream.

Class Name
----------

:javadoc:`org.yamcs.tctm.ArtemisTmDataLink`


Configuration Options
---------------------

stream (string)
    **Required.** The stream where data is emitted

address (string)
    Artemis address to bind to.

preserveIncomingReceptionTime (boolean)
    When ``true`` incoming reception times are preserved. When ``false`` each packet is tagged with a fresh reception timestamp. Default: ``false``.
