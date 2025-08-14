cFS Command Postprocessor
=========================

A postprocessor for handling `NASA (National Aeronautics and Space Administration)` cFS packets.

cFS command headers consist of a primary CCSDS Space Packet header (6 bytes), function code (1 byte), and checksum (1 byte).

This postprocessor will set the length and sequence count in the CCSDS primary header, and the checksum in the cFS secondary header.


Class Name
----------

:javadoc:`org.yamcs.tctm.cfs.CfsCommandPostprocessor`


Configuration
-------------

.. code-block:: yaml

   dataLinks:
     - name: tc-out
       # ...
       commandPostprocessorClassName: org.yamcs.tctm.cfs.CfsCommandPostprocessor
       commandPostprocessorArgs:
         swapChecksumFc: true


Configuration Options
---------------------

swapChecksumFc (boolean)
    Whether to swap the location of the checksum and the function code.

    This may be needed when using cFS prior to `this commit <https://github.com/nasa/cFE/pull/586/commits/ff3aa947bbd146747707f2ae13acfe3a30eb9e0a>`_ on little endian systems.

    Default: ``false``.
