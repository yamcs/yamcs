package org.yamcs.ui.eventviewer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.GeneratedMessage.GeneratedExtension;
import com.google.protobuf.InvalidProtocolBufferException;

class EventTableModel extends AbstractTableModel implements Observer {
    private static final long serialVersionUID = 1L;
    private static final Logger log=LoggerFactory.getLogger(EventTableModel.class);
    private static List<String> columnNames=new ArrayList<>();
    private List<GeneratedExtension<Event, Type>> extensions=new ArrayList<>();
    private ExtensionRegistry registry;

    /** Column indices */
    public static final int SOURCE_COL          = 0;
    public static final int GENERATION_TIME_COL = 1;
    public static final int RECEPTION_TIME_COL  = 2;
    public static final int EVENT_TYPE_COL      = 3;
    public static final int EVENT_TEXT_COL      = 4;
    public static final int FIRST_EXTENSION_COL = 5;

    /** Vector with all events */
    private Vector<Event>   allEvents           = null;

    /** Parallel data model with only visible events */
    private Vector<Event>   visibleEvents       = null;

    /** Filtering table */
    FilteringRulesTable     filteringTable      = null;

    public EventTableModel(FilteringRulesTable table, List<Map<String, String>> extraColumns) {
        columnNames.add("Source");
        columnNames.add("Generation Time");
        columnNames.add("Reception Time");
        columnNames.add("Type");
        columnNames.add("Description");
        
        allEvents = new Vector<Event>();
        visibleEvents = new Vector<Event>();
        filteringTable = table;
        filteringTable.registerObserver(this);
        if(extraColumns!=null) {
	        for(Map<String,String> col:extraColumns) {
	            if(registry==null) {
	                registry=ExtensionRegistry.newInstance();
	            }
	            try {
	                Class<?> extensionClazz=Class.forName(col.get("class"));
	                Field field=extensionClazz.getField(col.get("extension"));
	                @SuppressWarnings("unchecked")
	                GeneratedExtension<Event, Type> extension=(GeneratedExtension<Event, Type>)field.get(null);
	                extensions.add(extension);
	                registry.add(extension);
	                log.info("Installing extension "+extension.getDescriptor().getFullName());
	            } catch (IllegalAccessException e) {
	                log.error("Could not load extension class", e);
	                continue;
	            } catch (ClassNotFoundException e) {
	                log.error("Could not load extension class", e);
	                continue;
	            } catch (SecurityException e) {
	                log.error("Could not load extension class", e);
	                continue;
	            } catch (NoSuchFieldException e) {
	                log.error("Could not load extension class", e);
	                continue;
	            }
	            columnNames.add(col.get("label"));
	        }
        }
    }

    @Override
    public int getRowCount() {
        return visibleEvents.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Object value = null;
        Event event = visibleEvents.elementAt(rowIndex);

        if(columnIndex==SOURCE_COL) {
            value=event.getSource();
        } else if(columnIndex==EVENT_TEXT_COL) {
            value=event; // return event here to be compatible with older code
        } else if(columnIndex==EVENT_TYPE_COL) {
            value=event.getType();
        } else if(columnIndex==GENERATION_TIME_COL) {
            value=TimeEncoding.toString(event.getGenerationTime());
        } else if(columnIndex==RECEPTION_TIME_COL) {
            value=TimeEncoding.toString(event.getReceptionTime());
        } else if(columnIndex>=FIRST_EXTENSION_COL) {
            int extensionIndex=columnIndex-FIRST_EXTENSION_COL;
            if(columnIndex-FIRST_EXTENSION_COL<extensions.size()) {
                value=event.getExtension(extensions.get(extensionIndex));
            }
        }
        return value;
    }

    @Override
    public String getColumnName(int col) {
        return columnNames.get(col);
    }

    /**
     * Add list of events into model.
     * @param eventList List of events to be added.
     */
    public void addEvents(final List<Event> eventList) {
        List<Event> newEvents=eventList;
        if(!extensions.isEmpty()) {
            try {
                newEvents=new ArrayList<Event>(eventList.size());
                for(Event evt:eventList) {
                    newEvents.add(Event.parseFrom(evt.toByteArray(), registry));
                }
            } catch(InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
        allEvents.addAll(newEvents);

        int firstr = visibleEvents.size();
        for (Event event : newEvents) {
            if (filteringTable.isEventVisible(event)) {
                visibleEvents.add(event);
            }
        }
        int lastr = visibleEvents.size() - 1;

        if (firstr <= lastr) {
            fireTableRowsInserted(firstr, lastr);
        }
    }

    /**
     * Add single event into model.
     * @param event Event to be added.
     */
    public void addEvent(final Event event) {
        Event newEvent = event;
        if(!extensions.isEmpty()) {
            try {
                newEvent=Event.parseFrom(event.toByteArray(), registry);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
        allEvents.add(newEvent);

        if (filteringTable.isEventVisible(newEvent)) {
            visibleEvents.add(newEvent);
            int addedRow = visibleEvents.size() - 1;
            fireTableRowsInserted(addedRow, addedRow);
        }
    }

    /**
     * Access to event on the specific row.
     * @param row Row
     * @return Event on the row.
     */
    public Event getEvent(int row)
    {
        return visibleEvents.elementAt(row);
    }

    /**
     * Remove all events from the model.
     */
    public void clear()
    {
        fireTableRowsDeleted(0, getRowCount()-1);
        allEvents.clear();
        visibleEvents.clear();
    }

    /**
     * Apply new filtering rules. After the change of filtering rules the
     * visible data must conform with them.
     */
    public void applyNewFilteringRules()
    {
        visibleEvents.clear();

        for (Event event : allEvents)
        {
            if (filteringTable.isEventVisible(event))
            {
                visibleEvents.add(event);
            }
        }

        fireTableDataChanged();
    }

    @Override
    public void update(Observable o, Object arg)
    {
        applyNewFilteringRules();
    }
}
