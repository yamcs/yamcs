package org.yamcs.ui.archivebrowser;

import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class TMScale extends JSlider {
    private static final long serialVersionUID = 1L;
    final SimpleDateFormat f_yyyy_MMM = sdfFactory("yyyy\nMMM");
    final SimpleDateFormat f_MMM = sdfFactory("MMM");
    final SimpleDateFormat f_yyyy_MM_dd = sdfFactory("yyyy.MM\ndd");
    final SimpleDateFormat f_dd = sdfFactory("dd");
    final SimpleDateFormat f_DDD = sdfFactory("DDD");
    final SimpleDateFormat f_yyyy_DDD = sdfFactory("yyyy\nDDD");
    final SimpleDateFormat f_yyyy_DDD_HH = sdfFactory("yyyy/DDD\nHH");
    final SimpleDateFormat f_yyyy_MM_dd_HH = sdfFactory("yyyy.MM.dd/DDD\nHH");
    final SimpleDateFormat f_yyyy_DDD_HH_mm = sdfFactory("yyyy/DDD\nHH:mm");
    final SimpleDateFormat f_yyyy_MM_dd_HH_mm = sdfFactory("yyyy.MM.dd/DDD\nHH:mm");
    final SimpleDateFormat f_HH = sdfFactory("HH");
    final SimpleDateFormat f_HH_mm = sdfFactory("HH:mm");
    final SimpleDateFormat f_mm = sdfFactory("mm");

    static SimpleDateFormat sdfFactory(String format) {
        SimpleDateFormat sdf=new SimpleDateFormat(format);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    }
    
    
    Hashtable<Integer, JComponent> labels = new Hashtable<Integer, JComponent>();
    ZoomSpec zoom;
    long div;

    public TMScale() {
        super(JSlider.HORIZONTAL, 0, 100, 100);
        setUI(new TMScaleUI(this));
        setBackground(Color.LIGHT_GRAY);
        setPaintTicks(true);
        setPaintLabels(true);
        setPaintTrack(false);
        setFocusable(false);
        double wantedHeight = (new JLabel("x").getPreferredSize().getHeight()*2);
        setPreferredSize(new Dimension(getPreferredSize().width, (int) wantedHeight));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));

        /*final TMScale me = this;
            addComponentListener(new java.awt.event.ComponentAdapter() {
                public void componentResized(ComponentEvent e) {
                    debugLogComponent("scale resized", me);
                }
            });*/
    }
    private void addLabel(long instant, String labelString) {
        JComponent lab= new TwoLineLabel(this, labelString);
        labels.put(Integer.valueOf((int)((instant-zoom.startInstant) / div)), lab);
    }
    
    void setToZoom(ZoomSpec zoom ) {
        this.zoom=zoom;
        if(zoom.stopInstant-zoom.startInstant>(0x0FFFFFFFL)) {
            div=1000;
        } else {
            div=1;
        }
        labels.clear();
        SimpleDateFormat f;
        Calendar cal = getTruncatedCal(zoom.stopInstant);
        long instant;
        long measure = (long)(500 * zoom.pixelRatio / 1000); // number of seconds covered by 500 pixels
        //debugLog("setToZoom: measure " + measure + " ratio " + zoom.pixelRatio + " viewTimeWindow " + zoom.viewTimeWindow);
        if ( measure > 1 * 365 * 86400 ) {
            // the view is showing more than 2 years
            // align calendar to 1st of month, 00:00:00
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            for ( ; (instant=TimeEncoding.fromCalendar(cal))>zoom.startInstant ; cal.add(Calendar.MONTH, -1) ) {
                if ( cal.get(Calendar.MONTH) == 0 ) {
                    f = f_yyyy_MMM;
                } else if ( cal.get(Calendar.MONTH) % 3 == 0 ) {
                    f = f_MMM;
                } else {
                    continue;
                }
                addLabel(instant, f.format(cal.getTime()));
            }

        } else if ( measure > 180 * 86400 ) {
            // the view is showing more than 6 months
            // draw 1 month per major tick
            // align calendar to 1st of month, 00:00:00
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            for ( ; (instant=TimeEncoding.fromCalendar(cal))>zoom.startInstant ; cal.add(Calendar.MONTH, -1) ) {
                if ( cal.get(Calendar.MONTH) == 0 ) {
                    f = f_yyyy_MMM;
                } else {
                    f = f_MMM;
                }
                addLabel(instant, f.format(cal.getTime()));
            }

        } else if ( measure > 30 * 86400 ) {

            // the view is showing more than 30 days
            // draw 1 week per major tick, and the year/month for the first week of each month

            int d;

            // align calendar to Monday, 00:00:00
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);

            d = cal.get(Calendar.DAY_OF_MONTH);
            d = d >= 22 ? 22 : (d >= 15 ? 15 : (d >= 8 ? 8 : 1));
            cal.set(Calendar.DAY_OF_MONTH, d);

            for ( ; (instant=TimeEncoding.fromCalendar(cal))>zoom.startInstant; ) {
                if (d == 1) {
                    f = f_yyyy_MM_dd;
                } else {
                    f = f_dd;
                }
                addLabel(instant, f.format(cal.getTime()));
                if (d == 1) {
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    d = 22;
                } else {
                    d -= 7;
                }
                cal.set(Calendar.DAY_OF_MONTH, d);
            }

        } else if ( measure > 8 * 86400 ) {

            // the view is showing more than 8 days
            // draw 1 day per major tick, and DoY 001 + every 10th day displays the year

            int doy;

            // align calendar to 00:00:00
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            for ( ; (instant=TimeEncoding.fromCalendar(cal))>zoom.startInstant ; cal.add(Calendar.DAY_OF_YEAR, -1) ) {
                if ( cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY ) {
                    f = f_yyyy_MM_dd;
                } else {
                    f = f_dd;
                }
                addLabel(instant, f.format(cal.getTime()));
            }

        } else if ( measure > 86400 ) {

            // the view is showing more than 1 day
            // draw 1 day per major tick, and every day contains the year

            // align calendar to 00:00:00
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            for ( ; (instant=TimeEncoding.fromCalendar(cal))>zoom.startInstant ; cal.add(Calendar.DAY_OF_YEAR, -1) ) {
                f = f_yyyy_MM_dd;
                addLabel(instant, f.format(cal.getTime()));
            }

        } else if ( measure > 3 * 3600 ) {

            // the view is showing 3-24 hours
            // draw 1 hour per major tick, and midnight shows the DoY + year

            cal.set(Calendar.MINUTE, 0);
            for ( ; (instant=TimeEncoding.fromCalendar(cal))>zoom.startInstant ; cal.add(Calendar.HOUR_OF_DAY, -1) ) {
                if ( cal.get(Calendar.HOUR) == 0 ) {
                    f = f_yyyy_MM_dd_HH;
                } else {
                    f = f_HH;
                }
                addLabel(instant, f.format(cal.getTime()));
            }

        } else if ( measure > 600 ) {

            // the view is showing >10 minutes
            // draw 15 minutes per major tick, and every hour shows the DoY + year

            int m = cal.get(Calendar.MINUTE);
            cal.set(Calendar.MINUTE, m - m % 15); // rounding down to quarter of an hour
            for ( ; (instant=TimeEncoding.fromCalendar(cal))>zoom.startInstant ; cal.add(Calendar.MINUTE, -15) ) {
                if ( cal.get(Calendar.MINUTE) == 0 ) {
                    f = f_yyyy_MM_dd_HH;
                } else {
                    f = f_HH_mm;
                }
                addLabel( instant, f.format(cal.getTime()));
            }

        } else {
            // (highest resolution)

            // the view is showing <10 minutes
            // draw 1 minute per major tick, and every 10th shows the DoY + year

            for ( ; (instant=TimeEncoding.fromCalendar(cal))>zoom.startInstant ; cal.add(Calendar.MINUTE, -1) ) {
                if ( cal.get(Calendar.MINUTE) % 10 == 0 ) {
                    f = f_yyyy_MM_dd_HH_mm;
                } else {
                    f = f_HH_mm;
                }
                addLabel(instant, f.format(cal.getTime()));
            }
        }
        setLabelTable(labels);
        setMinimum(0);
        setMaximum((int)((zoom.stopInstant-zoom.startInstant) / div));
        
        updateFontSize();
    }
    
    private Font getLabelFont() {
        return getFont().deriveFont(getFont().getSize2D()-2);
    }
    
    /**
     * 
     * @param instant
     * @return a calendar with the seconds set to 0, suitable for "gross" operations
     */
    static Calendar getTruncatedCal(long instant) {
        DateTimeComponents dtc = TimeEncoding.toUtc(instant);
        Calendar cal=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(dtc.year, dtc.month-1,dtc.day, dtc.hour, dtc.minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        return cal;
    }
    
    static class TwoLineLabel extends JLabel {
        private static final long serialVersionUID = 1L;
        int lineHeight;
        String[] textlines;

        TwoLineLabel(TMScale scale, String text ) {
            super(text);
            //setForeground(UiColors.BORDER_COLOR);
            setFont(scale.getLabelFont());
            lineHeight = getPreferredSize().height;
            textlines = text.toUpperCase().split("\\n");
            if ( textlines.length > 1 ) {
                setText(textlines[0]);
                int w0 = getPreferredSize().width;
                setText(textlines[1]);
                int w1 = getPreferredSize().width;
                int newWidth = Math.max(w0, w1);
                setPreferredSize(new Dimension(newWidth, lineHeight * 2));
            } else {
                setText(textlines[0]);
                setPreferredSize(new Dimension(getPreferredSize().width, lineHeight * 2));
            }
            setVerticalAlignment(SwingConstants.TOP);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public void paint( Graphics g ) {
            if ( textlines.length > 1 ) {
                setText(textlines[0]);
                super.paint(g);

                setText(textlines[1]);
                g.translate(0, lineHeight);
                super.paint(g);
                g.translate(0, -lineHeight);
            } else {
                g.translate(0, lineHeight);
                super.paint(g);
                g.translate(0, -lineHeight);
            }
        }
    }
    
    /**
     * Tricky to change JSlider font size
     * See http://nadeausoftware.com/articles/2009/04/mac_java_tip_how_customize_aqua_sliders
     */
    public void updateFontSize() {
        for(JComponent comp:labels.values()) {
            JLabel lbl = (JLabel) comp;
            Font smallerFont = getLabelFont();
            lbl.setFont(smallerFont);
            lbl.setSize(lbl.getPreferredSize());
        }
    }
    
    static class TMScaleUI extends BasicSliderUI {
        // this UI class makes the thumb disappear, and it places the labels above the ticks

        TMScaleUI(JSlider slider) {
            super(slider);
        }
        
        

        @Override
        protected Dimension getThumbSize() {
            if ( slider.getOrientation() == JSlider.HORIZONTAL ) {
                return new Dimension(0, 0); // completely disable the thumb
            }
            return super.getThumbSize();
        }

        @Override
        protected void calculateTickRect() {
            // invoked before calculateLabelRect() so we cannot use labelRect.height
            super.calculateTickRect();
            if ( slider.getOrientation() == JSlider.HORIZONTAL ) {
                tickRect.y += getHeightOfTallestLabel();
            }
        }

        @Override
        protected void calculateLabelRect() {
            super.calculateLabelRect();
            if ( slider.getOrientation() == JSlider.HORIZONTAL ) {
                labelRect.y = trackRect.y + trackRect.height;
            }
        }

        @Override
        protected void calculateTrackBuffer() {
            trackBuffer = 0;
        }
        
        @Override
        public void paintTicks(Graphics g){
            if ( slider.getOrientation() == JSlider.HORIZONTAL ) {
                Dictionary dict = slider.getLabelTable();
                if ( dict != null ) {
                    g.setColor(Color.BLACK);
                    g.translate(0, tickRect.y);
                    for ( Enumeration e = dict.keys(); e.hasMoreElements(); ) {
                        Integer tsint = (Integer)(e.nextElement());
                        int ts = tsint.intValue();
                        int labelCenter = xPositionForValue(ts);
                        paintMajorTickForHorizSlider(g, tickRect, labelCenter);
                    }
                    g.translate(0, -tickRect.y);
                }
            } else {
                super.paintTicks(g);
            }
        }

        @Override
        protected int getTickLength() {
            return 6;
        }
        
        @Override
        protected int getHeightOfHighValueLabel() {
            return super.getHeightOfHighValueLabel() - 3;
        }
         @Override
        protected int getHeightOfLowValueLabel() {
            return super.getHeightOfLowValueLabel() - 3;
        }

        @Override
        protected void installListeners(JSlider jslider) {
            // disable mouse listeners
            super.installListeners(jslider);
            jslider.removeMouseListener(trackListener);
            jslider.removeMouseMotionListener(trackListener);
        }
    }

}