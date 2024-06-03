Timeline
========

The Timeline group within the Yamcs web interface provides access to the timeline functionality. 
Essentially, timelines show Items on Bands as a function of time.

`Bands` are labeled horizontal sections spanning the whole timeline. `Items` are labeled sections of time which can be displayed on Item-type Bands. 
Bands can be stacked vertically to create `Views`. Views can be visualized on the `Chart`.


Chart
-----

The Chart is where Views can be visualized over time. Views can be selected from the drop-down to the right of the "Timeline Chart" title.
The Items are the colored rectangles or diamonds on the Chart, located on their horizontal Bands. 
A red vertical line indicates the current time. The Chart can be zoomed in and out with the + and - buttons or with the middle mouse wheel.

The Chart can be panned with the arrow buttons or by holding down the left mouse button. Items can be clicked for editing.
From the Chart, users can also edit the current View, add an Event- or Activity-type Item (see Items section below for details), or take a snapshot of the Chart.


Views
-----

The Views page shows the list of existing Views. From this page Views can be edited by clicking on their label. 
Views can be deleted by selecting their checkbox and pressing the `Delete` button. New Views can be created with the `Create View` button.

New Views are composed by sequentially adding Bands from the Available list to the Selected list in the desired order. 
A Band can only be present once on a single View. 


Bands
-----

The Bands page shows the list of existing Bands. From this page Bands can be edited by clicking on their label. 
Bands can be deleted by selecting their checkbox and pressing the `Delete` button. New Bands can be created with the `Create Band` button.

Four types of Bands can be created:

* Time Ruler: displays time graduation, in a configurable timezone.
* Item Band: Band on which Items can be displayed. Dispays only items with matching Tags. The Band defines the default style of its Items.
* Spacer: creates an empty vertical space. Height can be configured.
* Commands: shows commands issued over time.  


Items
-----

The Items page shows the list of existing Items. From this page Items can be edited by clicking on their label. 
Items can be deleted by selecting their checkbox and pressing the `Delete` button. New Items can be created with the `Create Item` button.

Two types of Items can be created:

* Event item: gets added to the list of Items.
* Activity item: gets added to the list of Items and also to the list of Activities on the Activities page. 
  It will trigger at the specified time, and can be set Successful or Failed on the Activities page. 

Tags can be assigned to Items. Items will be displayed on Bands with matching Tags. Item start time and duration can be configured. 
Items will show as rectangles on the Chart unless they have a duration of 0, in which case they will appear as diamonds.
Items can be set to override the default style specified in a Band.

Items are also automatically added to the list when:

* the user selects "Send later..." when sending a command from the "Send a command" page   
* the user selects "Schedule" when running a command stack from the "Command stacks" page 
* the user selects "Run later..." when running a script from the "Run a script" page  

