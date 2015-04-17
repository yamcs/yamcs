package org.yamcs.ui.datepicker;

import net.sourceforge.jdatepicker.JDateComponentFactory;
import net.sourceforge.jdatepicker.impl.JDatePickerImpl;
import net.sourceforge.jdatepicker.impl.UtilCalendarModel;
import org.yamcs.TimeInterval;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import javax.swing.JFormattedTextField.AbstractFormatter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static java.util.Calendar.*;
/**
 * DatePicker class.
 * @author es
 *
 */
public final class DatePicker extends JPanel {
    MyDatePicker start, end;
    JComboBox fixedRangesComboBox;

    public DatePicker() { 
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        final String[] fixedRanges = {"Select Last...", "  3 months", "  6 months", "  12 months"};
        fixedRangesComboBox = new JComboBox(fixedRanges);
        fixedRangesComboBox.setSelectedIndex(0);

        start=new MyDatePicker();
        end=new MyDatePicker();

        fixedRangesComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                int idx = fixedRangesComboBox.getSelectedIndex();
                if (idx != 0) {
                	Calendar startCal = Calendar.getInstance();
                    startCal.set(Calendar.HOUR_OF_DAY, 0);
                    startCal.set(Calendar.MINUTE, 0);
                    startCal.set(Calendar.SECOND, 0);
                    startCal.set(Calendar.MILLISECOND, 0);
                    startCal.add(Calendar.DAY_OF_MONTH, 1);
                    end.setTime(startCal);
                	Calendar endCal = Calendar.getInstance();
                    endCal.add(Calendar.MONTH, -3*(1<<(idx-1)));
                    start.setTime(endCal);
                }
            }
        });
        add(start.datePicker);
        add(end.datePicker);
        add(fixedRangesComboBox);    
    }

    /**
     * Returns the starting date.
     */
    public long getStartTimestamp() {
        if(start.getValue()==null) return TimeEncoding.INVALID_INSTANT;
        
        return TimeEncoding.fromCalendar(start.getValue());
    }

    /**
     * Returns the ending date.
     */
    public long getEndTimestamp() {
        if(end.getValue()==null) return TimeEncoding.INVALID_INSTANT;
        
        return TimeEncoding.fromCalendar(end.getValue());
    }

    public TimeInterval getInterval() {
        TimeInterval ti=new TimeInterval();
        Calendar calStart=start.getValue();
        Calendar calEnd=end.getValue();
        if(calStart!=null) ti.setStart(TimeEncoding.fromCalendar(calStart));
        if(calEnd!=null) ti.setStop(TimeEncoding.fromCalendar(calEnd));
        return ti;
    }
    

    public void setStartTimestamp(long t) {
        start.setTime(TimeEncoding.toCalendar(t));
    }

    public void setEndTimestamp(long t) {
        end.setTime(TimeEncoding.toCalendar(t));
    }
    /**
     * wraps a date picker with a formatter that remembers hh:mm:ss.SSS in a private calendar
     */
    class MyDatePicker {
        JDatePickerImpl datePicker;
        YamcsDateTimeFormatter formatter=new YamcsDateTimeFormatter();
        UtilCalendarModel calendarModel = new UtilCalendarModel();
        Calendar calendar=null; //null means no value set

        MyDatePicker() {
            //calendar=Calendar.getInstance();
            //calendarModel.setValue(calendar);
            datePicker = (JDatePickerImpl) JDateComponentFactory.createJDatePicker(calendarModel, formatter);
            datePicker.getModel().setSelected(true);
            datePicker.setTextEditable(true);
            datePicker.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // if the date range is changed by date picker the fixed combo box becomes irrelevant,
                    if (fixedRangesComboBox.getSelectedIndex() != 0) {
                        fixedRangesComboBox.setSelectedIndex(0);
                    }
                }
            });
        }

        public Calendar getValue() {
            if(calendar==null) return null;
            Calendar cal=calendarModel.getValue();
            copyHhMmSs(calendar, cal);
            return cal;
        }

        public void setTime(Calendar cal) {
        	if(cal==null) {
        		calendar=null;
        		calendarModel.setValue(null);
        	} else {
        		if(calendar==null) calendar=Calendar.getInstance();
        		calendar.setTime(cal.getTime());
        		calendarModel.setValue(cal);
        	}
        }

        /**
         * this remembers the hh:mm:ss.SS
         */
        private  class YamcsDateTimeFormatter extends AbstractFormatter {
            private static final long serialVersionUID = -4371640422279341322L;

            @Override
            public Object stringToValue(final String dateString) throws ParseException {
            	if(dateString.isEmpty()) {
            		calendar=null;
            		return null;
            	}
            	
                Calendar cal = Calendar.getInstance();
                SimpleDateFormat simpleDateFormatter = new SimpleDateFormat();
                simpleDateFormatter.setCalendar(calendar);
                String[] patterns={"yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"};

                for(String p:patterns) {
                    simpleDateFormatter.applyPattern(p);
                    try {
                        Date date = simpleDateFormatter.parse(dateString);
                        
                        cal.setTime(date);
                        copyHhMmSs(cal, calendar);
                        return cal;
                    } catch (ParseException pe) {     }
                }

                JOptionPane.showMessageDialog(null,
                        "The date can only be in one of the following formats:\n"
                        + "yyyy-mm-dd\n"
                        + "yyyy-mm-ddThh:mm:ss\n"
                        + "yyyy-mm-ddThh:mm:ss.sss.",
                        "Unparseable date error!",
                        JOptionPane.ERROR_MESSAGE);

                throw new ParseException("Not a valid YAMCS DateTime string", 0);
            }

            @Override
            public String valueToString(final Object value) {
            	if(calendar==null) {
            		calendar=Calendar.getInstance();
            		calendar.set(Calendar.HOUR_OF_DAY, 0);
            		calendar.set(Calendar.MINUTE, 0);
            		calendar.set(Calendar.SECOND, 0);
            		calendar.set(Calendar.MILLISECOND, 0);
            	}
                if (value != null) {
                    Calendar cal = (Calendar) value;
                	
                    copyHhMmSs(calendar, cal);
                    
                    SimpleDateFormat simpleDateFormatter = new SimpleDateFormat();
                    simpleDateFormatter.applyPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

                    return simpleDateFormatter.format(cal.getTime());

                } else {
                    calendar=null;
                    return null; 
                }       
            }
        }
        void copyHhMmSs(Calendar from, Calendar to) {
            to.set(HOUR_OF_DAY, from.get(HOUR_OF_DAY));
            to.set(MINUTE, from.get(MINUTE));
            to.set(SECOND, from.get(SECOND));
            to.set(MILLISECOND, from.get(MILLISECOND));
        }
    }

}