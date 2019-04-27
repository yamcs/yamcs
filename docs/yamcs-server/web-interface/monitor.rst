Monitor
=======

The Monitor module within the Yamcs web interface provides typical operational views.

The Monitor Module is always visited for a specific Yamcs instance and processor. Every view has in the top right corner an indicator that shows the current processor and that shows the time for that processor. From this widget, you can choose to start a replay of past data. When that happens, you will switch to this replay processor and would see the widget reflecting the replay time.


Displays
--------

Shows the displays or display resources that are known by Yamcs Server for the selected instance. The displays in this view are presented in a file browser with the usual operations to rename, move or create. Clicking on a display file opens the display. If there is incoming telemetry this will be received by the opened display file.

Note that only some display types are supported by the Yamcs web interface. The following provides an overview of the current state:

.. list-table::
    :header-rows: 1

    * - Extension
      - Display Type
      - View
      - Edit
    * - ``opi``
      - Yamcs Studio displays
      - Planned
      - | No plans to support
        | (use Yamcs studio)
    * - ``uss``
      - USS displays
      - Advanced support
      - | No plans to support
        | (use USS Editor)
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

Displays may be visualized in full screen, which helps remove distractions. This will not scale the display, so make use of the zoom in/out buttons before going full screen if you would like your display to appear larger.

Layouts
-------

A layout allows to combine multiple displays in a single view. This is particularly useful if your displays are small. Layouts are personal and are linked to your user account. Create a layout via the "Create Layout" button in the toolbar. Open any number of displays via the sidebar and organize them together. Click "Save" when you want to save the changes to your layout for later use.

Individual display frames can be zoomed in or out by dragging the corner in the right-bottom.

A layout may be visualized in full screen, which helps remove distractions.


Events
------

This section provides a view on Yamcs events. By default only the latest events within the last hour get shown. The view offers ample filter options to change which events are shown. The table is paged to prevent overloading the browser. If you like to see beyond the current page, you can click the button 'Load More' at the bottom of the view. Alternatively you can choose to click the 'Download Data' button at the top right. This will trigger a download of the events in CSV format. The download will apply the same filter as what is shown in the view.

The Events table can also monitor incoming events on the current processor. Do so by clicking the play button in the top toolbar. You may stop the live streaming at any time by clicking the pause button.

The Events table has a severity filter. This filter allows defining the **minimum** severity of the event. Events that are more severe than the selected severity will also be shown. By default the severity filter is set to the lowest severity, ``Info``, which means that all events will be shown.

With the right privilege, it is possible to manually post an event. You can enter an arbitrary message and assign a severity. The time of the event will by default be set to the current time, but you can override this if preferred. The source of an event created this way will automatically be set to ``User`` and will contain a ``user`` attribute indicating your username.


Alarms
------

Shows an overview of the current alarms. Alarms indicate parameters that are out of limits.


Commands
--------

Shows the latest issued commands.


TM Archive
----------

This view allows inspecting the content of the TM Archive, as well as retrieving data as packets. Data is grouped by packet name in bands. For each band, index blocks indicate the presence of data at a particular time range. Note that a single index block does not necessarily mean that there was no gap in the data. When zooming in, more gaps may appear.

The view can be panned by grabbing the canvas. For long distances you can jump to a specific location via the ``Jump to...`` button.

This view shows the current mission time with a vertical locator.

.. note::

    While the now locator follows mission time, the rendered blocks do not follow realtime. You can force a refresh by panning the canvas or refreshing your browser window.


In the top toolbar there are a few actions that only become active once you make a horizontal range selection. To make such a selection you can start a selection on the timescale band. Alternatively you may also select a range by simply clicking an index block. Selecting a range allows you to start a replay for that range, or to download raw packet data.
