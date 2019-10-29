Telemetry
=========

The Telemetry group within the Yamcs web interface provides access to monitoring-related pages.

Parameters
----------

This page lists all parameters. Each parameter can be accessed individually to see the latest or archived values. Numeric parameters can also be charted.


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
