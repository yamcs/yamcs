XTCE TM Recorder
================

Records XTCE TM sequence containers. This service stores the data coming from one or more streams into a table ``tm``. The tm table has a column called ``pname`` which stands for packet name. The main task of this service is to assign the value for that column; all the other colums will come directly from the tm stream as provided by the data links.

The pname is a fully qualified name of a matching XTCE containter. In the XTCE hierarchy some containers have a flag ``useAsArchivingPartition`` (this flag is an Yamcs extension to XTCE). That flag is used to determine the container that will give its name to the packet when saved into the tm table - the name of the lowest level matching container with this flag set is chosen as the pname. If no container matches, then the name of the root container will be used. 


Class Name
----------

:javadoc:`org.yamcs.archive.XtceTmRecorder`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.archive.XtceTmRecorder

    streamConfig:
      tm:
        - tm_realtime
        - tm_dump

With this configuration containers coming from both the tm_realtime and tm_dump streams are stored into the table ``tm``.


Configuration Options
---------------------

streams (list of strings)
    The streams to record. When unspecified, all ``tm`` streams defined in ``streamConfig`` are recorded.
