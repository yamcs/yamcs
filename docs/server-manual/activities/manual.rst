Manual Activities
=================

Manual activities are background activities whose execution status is managed outside of Yamcs, typically human-initiated. For example you may want to express that an activity is to take place where an operator must perform some action prior to running a script activity.

Manual activities can be monitored in the Yamcs UI together with automated activities.


Execution
---------

Manual activities do not stop automatically. You are expected to stop them yourself by choosing one of the options :guilabel:`Set successful` or :guilabel:`Set failed`. In the latter case, you will be prompted to provide a reason for failure.


Yamcs UI
--------

The Yamcs UI does not support **immediate** execution of a manual activity.

To start a manual activity **at a later time**, go to :menuselection:`Timeline --> Chart`, then select :menuselection:`Add item --> Activity item`.


Python Yamcs Client
-------------------

Run a manual activity one minute from now:

.. code-block:: python

    from datetime import datetime, timedelta, timezone

    from yamcs.client import Item, ManualActivity, YamcsClient

    client = YamcsClient("http://localhost:8090")
    timeline = client.get_timeline_client("simulator")

    now = datetime.now(tz=timezone.utc)

    item = Item()
    item.name = "Start simulator"
    item.start = now + timedelta(minutes=1)
    item.duration = timedelta(minutes=5)  # Planned duration
    item.activity = ManualActivity()
    timeline.save_item(item)
