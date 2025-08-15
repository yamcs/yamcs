Packet Input Stream
===================

When receiving a stream of packets on a stream-based data link (for example, :doc:`../tcp-tm-data-link`), Yamcs needs to be told as part of that link's configuration, how consecutive packets can be distinguished from each other.

This type of information is not part of the Mission Database, it is handled exclusively through Yamcs configuration.

A number of common implementations are provided. If necessary, you may also create yourself a custom implementation, by implementing the :javadoc:`~org.yamcs.tctm.PacketInputStream` interface.

.. note::
    For data links where input is naturally split into frames, like UDP, a packet input stream is not used.

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    fixed
    ccsds
    generic
