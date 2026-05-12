Timeline Activities
===================

Timeline Activities capture the execution of an operational artifact (command, stack, script, ...).


Yamcs UI
--------

The Timeline chart in the Yamcs UI does not directly allow to submit activities. Instead you can do so from dedicated pages:

* To execute a command activity at a later time: go to :menuselection:`Commanding --> Send a command`, then select :menuselection:`Send later...`.

* To execute a stack activity at a later time: go to :menuselection:`Procedures --> Stacks`, then on the page of a specific stack, select :menuselection:`Schedule`.

* To execute a script activity at a later time: go to :menuselection:`Procedures --> Run a script`, then select :menuselection:`Run later...`.

In any of these cases, a dialog will open where you can specify the activity's details:

You need to provide at least a **Label** and the **Execution time**. The duration will automatically be set to 0 (unknown).

The **Start automatically** field indicates whether the activity will automatically start at the planned start time. If unchecked it will instead go into a ``READY`` mode, waiting for the user to indicate that the activity is actually underway.

Optionally you can associate tags with this activity. Tags are free-form text fields. This information is used by :doc:`bands/item-band` to select which activities are rendered: any activity that has at least one tag that matches one of the band's tags will be displayed on that specific band.

If you don't associate any tags, the activity will only be displayed on bands that do not have any tags defined.

.. note::
    Activities do not follow all style preferences of the band (e.g. background color). Their appearance is controlled by the Yamcs UI and depends on their execution status.


Python Yamcs Client
-------------------

Example code is provided in:

* :doc:`../../activities/commands`
* :doc:`../../activities/stacks`
* :doc:`../../activities/scripts`
