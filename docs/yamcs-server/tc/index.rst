Commanding
==========

Yamcs supports XTCE concepts for commanding. Commands have constraints (preconditions) and verifiers (postconditions). The constraints are checked before issuing an command and the verifiers are run after the command has been issued to verify the different stages of execution.

In addition to the constraints/verifiers, Yamcs also implements the concept of command queue. This allows an operator to inspect commands of other users before being issued. It also allows to block completely commands from users during certain intervals (this effect can also be obtained with a constraint).

The commands and arguments are formatted to binary packets based on the XTCE definition.


Command Significance
--------------------

Yamcs uses the XTCE concept of command significance. Each command's significance can have one of this values none (default), watch, warning, distress, critical or severe.

In addition to the significance, the command has a message explaining why the command has the given significance.

Currently, Yamcs Server does not check or impose anything based on the significance of the command. In the future, the privileges may be used to restrict users that can send commands of high significance. However, currently the information (significance + reason) is only given to an external application (Yamcs Studio) to present it to the user in a suitable manner.

The command significance can be defined in the Excel Spreadsheet in the CommandOptions tab:

.. image:: _images/significance.png
    :alt: Significance
    :align: center


Command Queues
--------------

When a command is sent by an external user, it goes first into a queue. Privileges are checked before the command is put into the queue, so if the user doesn't have the privilege for the given command, the command will be rejected and not appear at all in the queue.

The available queues are defined in the file ``etc/command-queue.yaml``.

.. code-block:: yaml
    :caption: command-queue.yaml

    ops:
      state: enabled
      significances: [none]

    ops-critic:
      state: enabled
      significances: [watch, warning, distress, critical, severe]
      stateExpirationTimeS: 300

Each queue has a name, a default state and a list of roles. The commands of a user logging in with a given role will be put in the first queue for which the user has privileges. A queue can be in three different states:

Enabled
  The commands are sent immediately.

Blocked
  The commands are accepted into the queue but need to be manually sent.

Disabled
  The commands are rejected.

There is always a command-queue called 'default' whose state is enabled. If a command comes from a user without privilege for any of the defined queues, the command will be put in the default queue. The default queue can be redefined in order to have a different state.

Control over the command queues, requires the ``ControlCommandQueue`` privilege.


Transmission Constraints
------------------------

When the is set to be released from the queue (either manually by an operator or automatically because the queue was in the Enabled state), the transmission constraints are verified.

The command constraints are conditions set on parameters that have to be met in order for the command to be releasd. There can be multiple constraints for each command and each constraint can specify a timeout which means that the command will fail if the constraint is not met within the timeout. If the timeout is 0, the condition will be checked immediately.

The transmission constraints can be defined in the Excel Spreadsheet in the CommandOptions tab.

.. image:: _images/constraints.png
    :alt: Constraints
    :align: center

Currently it is only possible to specify the transmission constraints based on parameter verification. This corresponds to  Comparison and ComparisonList in XTCE. In the future it will be possible to specify transmission constraints based on algorithms. That will allow for example to check for specific values of arguments (i.e. allow a command to be sent if cmdArgX > 3).
