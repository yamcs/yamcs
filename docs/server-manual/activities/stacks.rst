Stack Activities
================

Yamcs can run operator stacks as a background activity.


Activity Options
----------------

bucket (string)
    **Required.** Bucket where to locate the stack.

stack (string)
    **Required.** Relative path to the stack file in the bucket.

processor (string)
    Choose the applicable TM/TC processor.

    This defaults to the first defined processor with commanding capabilities, which by convention is usually called ``realtime``.


Execution
---------

Stack entries will be executed one by time. If any entry fails, the activity fails.


Yamcs UI
--------

The Yamcs UI does not support **immediate** execution of a stack in the background. You can however run it interactively from :menuselection:`Procedures --> Stacks`.

To execute a stack **at a later time**, click the button :guilabel:`Schedule`. You will be asked to enter the desired execution time. This will create an activity *item* in the :doc:`../timeline/index`.


Python Yamcs Client
-------------------

Run a stack activity one minute from now:

.. code-block:: python

    from datetime import datetime, timedelta, timezone

    from yamcs.client import CommandStackActivity, Item, YamcsClient

    client = YamcsClient("http://localhost:8090")
    timeline = client.get_timeline_client("simulator")

    now = datetime.now(tz=timezone.utc)

    item = Item()
    item.start = now + timedelta(minutes=1)
    item.duration = timedelta(seconds=1)  # Planned duration
    item.activity = CommandStackActivity(
        bucket="stacks",
        stack="stack.ycs",
    )
    timeline.save_item(item)
