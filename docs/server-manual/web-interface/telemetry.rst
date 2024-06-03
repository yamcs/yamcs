Telemetry
=========

The Telemetry group within the Yamcs web interface provides access to monitoring-related pages.

Packets
-------

This page lists all received packets. The list needs to be manually refreshed with the `circular arrow` icon. Details appear when clicking on a packet. 
Packets can be extracted into their parameters by clicking `Extract`. 
Packet Hex or Binary can be copied, or raw telemetry can be downloaded by clicking on the `3-dots` icon.


Parameters
----------

This page shows all parameters. 
Each parameter can be accessed individually to see the latest value (`Summary` tab), archived values (`Historical Data` tab) or the alarms related to this parameter (`Alarm History tab`). 
Numeric parameters can be charted (`Chart` tab). 
Historical data for the selected parameter can be downloaded by clicking `Export CSV` on the Historical Data tab, and picking a range, delimiter and interval.


Parameter Lists
---------------

This page allows users to group parameters together into lists. New lists can be created with the `Create List` button. 
The list  name for the list, a description and add the parameters by parameter names or with glob patterns.
Parameter lists can be selected to show latest value (`Realtime` tab) or archived values (`Historical Data` tab).
Historical data for the selected list can be downloaded by clicking `Export CSV` on the Historical Data tab, and picking a range, delimiter and interval. 


Displays
--------

This page shows the list of displays or display resources that are known by Yamcs Server for the selected instance. 
Displays are stored within the "displays" storage bucket. 
Yamcs Studio displays (`.opi`) can be visualized in the Yamcs web interface. They can be uploaded with the `Upload Files` button. 
Additionally, Parameter Tables (`.par`) can be created, by clicking the `Create Display` button, entering a name and adding parameters.
Items in the Displays page list can be renamed, downloaded or deleted. Clicking on a display file opens the display. 
If there is incoming telemetry, it will be received by the opened display file and the display will update accordingly.

Note that only some display types are supported by the Yamcs web interface. The following provides an overview of the current state:

.. list-table::
    :header-rows: 1

    * - Extension
      - Display Type
      - View
      - Edit
    * - ``opi``
      - Yamcs Studio displays
      - Basics
      - | No plans to support
        | (use Yamcs studio)
    * - ``par``
      - Parameter tables
      - Full support
      - Full support

In addition there is file preview support for the following display resources:

.. list-table::
    :header-rows: 1

    * - Extension
      - Resource Type
      - View
      - Edit
    * - ``png``, ``gif``, ``bmp``, ``jpg``, ``jpeg``
      - Image
      - Full support
      - No plans to support
    * - ``js``
      - Script file
      - Full support
      - Planned

Any other file is displayed in a basic text viewer.


Replaying telemetry
-------------------

Telemetry replays can be triggered from any instance-scoped page by clicking the mission time in the page toolbar and selecting **Replay from date**.

In the dialog that opens, you can choose a replay range. Yamcs will start a *replay* processor which will run in parallel to the *realtime* processor.

The UI will switch to this replay processor, causing pages that normally would show realtime telemetry, to show replayed telemetry instead.
