Model
=====

.. image:: _images/timeline-model.png
    :alt: Timeline model
    :align: center

* **Views** are composed of horizontal bands. A band may be used in any number of views, it is not owned by the view.

* **Bands** are horizontal lanes showing different types of content. Typically you would include at least a *Time Ruler* band. *Item Band* is a general purpose band which can render *items*. The items that a band should render can be filtered through the use of tags. Other band types include the ability to render parameter plots or value changes.

* **Items** have a start and a duration. Items can be associated with a set of tags. Any *Item Band* with the same tags, will show such items. Three different kinds of items exist:

  * A **Timeline Event** is for keeping track of something that will happen at a specific time (for example: a pass).
  * A **Timeline Activity** captures the definition of a Yamcs activity (command, stack, script, ...).
  * A **Timeline Task** is for keeping track of something that you need to do yourself. Unlike with activities, the execution is not managed by Yamcs.

  **Timeline Activities** and **Timeline Tasks** are picked up by the activity scheduler for automatic execution.
