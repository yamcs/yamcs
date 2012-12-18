package org.yamcs.ui.datepicker;

import javax.swing.JFrame;

import org.yamcs.utils.TimeEncoding;

/**
 * Test the DatePicker class.
 * @author es
 *
 */
public final class DatePickerTest {
    /**
     * Schedule a job for the event-dispatching thread:
     * creating and showing this application's GUI.
     * @param args arguments to the main method.
     */
    static DatePicker myDatePicker;
    public static void main(final String[] args) throws InterruptedException {
        TimeEncoding.setUp();
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                myDatePicker = new DatePicker();

                JFrame myFrame = new JFrame();
                myFrame.getContentPane().add(myDatePicker);
                myFrame.setSize(800, 100);
                myFrame.setVisible(true);  
            }
        });

        while(true) {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    System.out.println("current start: "+ TimeEncoding.toString(myDatePicker.getStartTimestamp()));
                    System.out.println("current end: "+TimeEncoding.toString(myDatePicker.getEndTimestamp()));
                }
            });
            Thread.sleep(3000);
        }
    }
}
