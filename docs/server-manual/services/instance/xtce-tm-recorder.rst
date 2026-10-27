XTCE TM Recorder
================

Records XTCE TM sequence containers. This service stores the data coming from one or more streams into a table ``tm``. The tm table has a column called ``pname`` which stands for packet name. The main task of this service is to assign the value for that column; all the other columns will come directly from the tm stream as provided by the data links.

The pname is a fully qualified name of a matching XTCE container. In the XTCE hierarchy some containers have a flag ``useAsArchivingPartition`` (this flag is an Yamcs extension to XTCE). That flag is used to determine the container that will give its name to the packet when saved into the tm table - the name of the lowest level matching container with this flag set is chosen as the pname. If no container matches, then the name of the root container will be used. 


Class Name
----------

:javadoc:`org.yamcs.archive.XtceTmRecorder`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

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

receptionTimeIndex (boolean)
    Default: ``false``. Enables provisioning of the ``INDEX(rectime, pname)`` secondary index for reception-time packet browsing. Existing archive rows are indexed in the background while recording continues. Reception-time queries are available only after the build completes. The build resumes from durable checkpoints after a restart. Once installed, the index continues to be maintained even if this provisioning option is disabled.

    Enable it on the recorder service:

    .. code-block:: yaml

        services:
          - class: org.yamcs.archive.XtceTmRecorder
            args:
              receptionTimeIndex: true

    The packets page offers Generation time / Reception time and Newest first / Oldest first controls. Both the selected time window and ordering use the selected clock. Raw dump exports use the same selection. Packets without a stored reception time are excluded from reception-time queries, and their count is reported with archive information.

    Index status is available at ``GET /api/archive/{instance}/packets:info``. Administrators with ``ControlArchiving`` can start a fresh background rebuild with ``POST /api/archive/{instance}/packets:rebuildReceptionTimeIndex``. Generation-time browsing remains available during a build. Reception queries return HTTP 503 while the index is unavailable; they never fall back to a full archive scan and sort.

    The index consumes additional storage and adds journal writes for time-partitioned archives. Measure recording throughput before enabling it on a production instance: the recovery protocol synchronizes the source and index WALs for each cross-database mutation. See the TM reception-time implementation notes for validation commands and measured costs.
