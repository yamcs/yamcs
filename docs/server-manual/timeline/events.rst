Timeline Events
===============

Timeline Events are for keeping track of things that occur at a specific time. Events start on time and end on time. They do not have an execution status, or rather they always complete successfully.

An example of an event is to show the predicted contact between a satellite and a ground station.


Yamcs UI
--------

To create a timeline event, go to :menuselection:`Timeline`, then select :menuselection:`Create event`.

.. rubric:: General

You will need at least a **Start** and a **Duration**. The **Name** field is recommended, though optional because there are times where you just want to colorize a time range without any text (for example a Day/Night indicator).

Optionally you can associate tags with this event. Tags are free-form text fields. This information is used by :doc:`bands/item-band` to select which events are rendered: any event that has at least one tag that matches one of the band's tags will be displayed on that specific band.

If you don't associate any tags, the event will only be displayed on bands that do not have any tags defined.

.. rubric:: Styles

When creating a specific event you may override some of the item's band default appearance options:

Background color
    RGB color of the background fill of the item's box.

Text color
    RGB color for the text of an item.

Text size
    Text height in pixels.

Margin left
    Whitespace in pixels between the start of the item's box and the item's text.

Border color
    RGB color for the border outlining the item's box.

Border width
    Thickness in pixels of the item's border.

Corner radius
    Corner radius of the item's box in pixels.


To see events, add a band to your view of type :doc:`bands/item-band`.


Python Yamcs Client
-------------------

Keep track of a 6-minute pass starting one hour from now:

.. code-block:: python

    from datetime import datetime, timedelta, timezone

    from yamcs.client import TimelineEvent, YamcsClient

    client = YamcsClient("http://localhost:8090")
    timeline = client.get_timeline_client("simulator")

    now = datetime.now(tz=timezone.utc)

    item = TimelineEvent(
        name="Pass",
        start=now + timedelta(hours=1),
        duration=timedelta(minutes=6),
        background_color="#b9ff66",
        border_color="green",
    )
    timeline.save_item(item)

.. note::
    If you do not see the item appearing on your timeline view, ensure it contains an :doc:`bands/item-band` without defined tags.
