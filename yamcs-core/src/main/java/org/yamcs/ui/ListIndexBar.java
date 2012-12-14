package org.yamcs.ui;


import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.*;
import java.util.List;

/**
 * Simple component for displaying a visual representation of the index of a list of items with markers for specific
 * indices in that list. Markers can be clicked on to fire a selection event to interested listeners.
 * 
 * Author: Darren Scott, blog.darrenscott.com
 */
public class ListIndexBar extends JComponent {
	private static final long serialVersionUID = 1L;
	private int itemCount;
	private double scaleFactor;
	private int markerHeight;

	// set of list indices associated with items to be marked
	private Set<Integer> markerSet = new HashSet<Integer>();

	// the index of the currently highlighted marker index
	// gets set when the pointer hovers over a marker and cleared when the mouse is moved off a marker
	// or the pointer leaves the component completely
	private int highlightedIndex = -1;

	// keep track of listeners interested in marker selection events
	private List<ListSelectionListener> listeners = new ArrayList<ListSelectionListener>();
	
	public ListIndexBar(int itemCount) {
		this.itemCount = itemCount;
		recalc();

		// add a mouse motion listener to track the current highlighted marker
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				// calculate the list index which is under the mouse pointer
				int pos = (int) (ListIndexBar.this.itemCount * (e.getPoint().getY() / getHeight()));
				boolean found = false;
				// Added by atu
				// Have to allow for a modelled list having more entries than
				// pixels to view.
				if( scaleFactor < 1 ) {
					for (int i = -markerHeight; i < markerHeight; i++) {
						if( markerSet.contains( pos + i ) ) {
							// we're over one of the markers so record the index and change the cursor
							highlightedIndex = pos + i;
							setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
							found = true;
							break;
						}
					}
				} else {
					if( markerSet.contains( pos ) ) {
						// we're over one of the markers so record the index and change the cursor
						highlightedIndex = pos;
						setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
						found = true;
					}
				}
				if (!found) {
					// we're not over any marker so clear the highlighted index and reset the cursor
					highlightedIndex = -1;
					setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				}
			}
		});

		// add a mouse listener to handle mouse clicks on markers
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (highlightedIndex != -1) {
					ListSelectionEvent event = new ListSelectionEvent(ListIndexBar.this, highlightedIndex, highlightedIndex, false);
					for (ListSelectionListener listener : listeners) {
						listener.valueChanged(event);
					}
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				// clear the highlighted index when we leave this component
				highlightedIndex = -1;
			}
		});

		// give the component a min and preferred size
		setMinimumSize(new Dimension(16, 60));
		setPreferredSize(new Dimension(16, 60));
	}

	public void addSelectionListener(ListSelectionListener listener) {
		listeners.add(listener);
	}

	public void removeSelectionListener(ListSelectionListener listener) {
		listeners.remove(listener);
	}

	public int getItemCount() {
		return itemCount;
	}

	public void setItemCount(int itemCount) {
		this.itemCount = itemCount;
		recalc();
		repaint();
	}

	private void recalc() {
		scaleFactor = getHeight() / (double) itemCount;
		markerHeight = Math.max(2, (int) scaleFactor);
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		recalc();
	}

	@Override
	public void setBounds(Rectangle r) {
		super.setBounds(r);
		recalc();
	}

	public Set<Integer> getMarkerSet() {
		return markerSet;
	}

	public void addMarkers(Collection<Integer> markers) {
		markerSet.addAll(markers);
		repaint();
	}

	public void addMarker(int index) {
		// Added by atu
		markerSet.add( new Integer(index) );
	}
	
	public void removeMarkers(Collection<Integer> markers) {
		markerSet.removeAll(markers);
		repaint();
	}

	public void clearMarkers() {
		markerSet.clear();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {

		// cast to a Graphics2D so we can do more with it
		Graphics2D g2 = (Graphics2D) g;

		// paint or clear the background depending on whether this component is opaque or not
		Composite composite = g2.getComposite();
		g2.setColor(getBackground());
		if (!isOpaque()) {
			// if not opaque, set the alpha composite to clear the background
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.DST));
		}
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setComposite(composite);

		// markers will be drawn with the foreground colour
		g2.setColor(getForeground());

		int pos;
		for (Integer marker : markerSet) {
			// for each marker, calculate the appropriate Y position and paint a marker of required size
			pos = (int) (marker * scaleFactor);
			g2.fillRect(0, pos, getWidth(), markerHeight);
		}
	}

}
