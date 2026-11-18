Events
======

This section provides a view on Yamcs events. By default only the latest events within the last hour get shown. The view offers ample filter options to change which events are shown. The table is paged to prevent overloading the browser. If you like to see beyond the current page, you can click the button 'Load More' at the bottom of the view. Alternatively you can choose to click the 'Download Data' button at the top right. This will trigger a download of the events in CSV format. The download will apply the same filter as what is shown in the view.

The Events table can also monitor incoming events on the current processor. Do so by clicking the play button in the top toolbar. You may stop the live streaming at any time by clicking the pause button.

The Events table has a severity filter. This filter allows defining the **minimum** severity of the event. Events that are more severe than the selected severity will also be shown. By default the severity filter is set to the lowest severity, ``Info``, which means that all events will be shown.

With the right privilege, it is possible to manually post an event. You can enter an arbitrary message and assign a severity. The time of the event will by default be set to the current time, but you can override this if preferred. The source of an event created this way will automatically be set to ``User`` and will contain a ``user`` attribute indicating your username.

Extra event fields
------------------

The search query supports string comparisons against any event extra field. For example,
``extra.subsystem = "FSW"`` selects events labelled with subsystem FSW, while
``source = "APID_1" AND extra.subsystem = "FSW"`` also restricts their source.
Use ``NOT extra.subsystem = "FSW"`` to exclude that subsystem. These filters apply to
archive queries, realtime subscriptions and CSV export selection. Missing fields do not
match equality comparisons.

To display an extra field, configure the WebPlugin in ``etc/yamcs.yaml``:

.. code-block:: yaml

    yamcs-web:
      events:
        extraColumns:
          - id: subsystem
            label: Subsystem
            after: source
            visible: true

Configured fields appear in the column chooser and search suggestions. Click an extra
column header to sort ascending, descending, or clear the sort. Sorting is textual and
applies to loaded events, including newly loaded pages and incoming realtime events.
Missing values appear last. Equal values retain newest-first chronological order.
The archive is still fetched in chronological pages: sorting does not request a global
sort of the entire archive. Clearing the column sort restores chronological order.
