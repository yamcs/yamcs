Command Activities
==================

Yamcs is capable of executing a single command as a background activity.

.. note::
   Yamcs does not enforce all commands to be activities, rather it has an API to send commands directly. The capability to wrap a command in an activity is primarily intended for use with planned activities.


Activity Options
----------------

command (string)
    **Required.** Fully-qualified command name.

args (map)
    Command arguments mapped from name to value.

processor (string)
    Choose the processor that will handle the command instruction.

    This defaults to the first defined processor with commanding capabilities, which by convention is usually called ``realtime``.

extra (map)
    Additional command options mapped from name to value (implementation-specific).

stream (string)
    Specify the low-level Yamcs stream where the command is emitted.

    If undefined, Yamcs will automatically select the target stream (first matching ``tc`` stream under the ``streamConfig`` configuration block. See :doc:`../data-management/streams`.


Execution
---------

The activity finishes successfully when the command could be encoded and submitted to a link. It does not currently track any acknowledgments or command completion.


Yamcs UI
--------

The Yamcs UI does not support **immediate** execution of command activities, because you can send commands directly without the notion of an activity.

To execute a command **at a later time**, go to :menuselection:`Commanding --> Send a command`. Enter the command form, then choose the option :guilabel:`Send later...`. You will be asked to enter the desired execution time. This will create an activity *item* in the :doc:`../timeline/index`.


Python Yamcs Client
-------------------

Run a command activity one minute from now:

.. code-block:: python

    from datetime import datetime, timedelta, timezone

    from yamcs.client import CommandActivity, Item, YamcsClient

    client = YamcsClient("http://localhost:8090")
    timeline = client.get_timeline_client("simulator")

    now = datetime.now(tz=timezone.utc)

    item = Item()
    item.start = now + timedelta(minutes=1)
    item.duration = timedelta(seconds=1)  # Planned duration
    item.activity = CommandActivity(
        command="/YSS/SIMULATOR/SWITCH_VOLTAGE_ON",
        args={"voltage_num": 1},
    )
    timeline.save_item(item)
