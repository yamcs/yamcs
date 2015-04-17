package org.yamcs.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.XtceDb;

/**
 * Models an XtceDb with the root as the root SpaceSystem, immediate children
 * as the SequenceContainers, and leaves as their ParameterEntry instances.
 * 
 * @author atu
 *
 */
public class XtceDbTreeModel implements TreeModel {
	protected XtceDb xtcedb;
	protected SpaceSystem root;
	protected List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();
	protected List<SequenceContainer> visibleContainers = new ArrayList<SequenceContainer>();
	protected Map<SequenceContainer, List<ParameterEntry>> visibleEntries = new HashMap<SequenceContainer, List<ParameterEntry>>();
	
	public XtceDbTreeModel( XtceDb db ) {
		xtcedb = db;
	}
	
	/**
	 * Creates the cache where we map the modelled hierarchy to the Xtce
	 * instances.
	 */
	protected void createCache() {
		visibleContainers.clear();
		visibleEntries.clear();
		if( xtcedb == null ) {
			return;
		}
		for( SequenceContainer sc : xtcedb.getSequenceContainers() ) {
			visibleContainers.add( sc );
			visibleEntries.put(sc, getParameterEntries( sc ) );
		}
		Collections.sort( visibleContainers, new OpsNameComparator() );
	}
	
	protected List<ParameterEntry> getParameterEntries( Object container ) {
		List<ParameterEntry> entryList = new ArrayList<ParameterEntry>();
		if( container instanceof SequenceContainer ) {
			for( SequenceEntry se : ((SequenceContainer)container).getEntryList() ) {
				if( se instanceof ParameterEntry ) {
					entryList.add( (ParameterEntry)se );
				} else if ( se instanceof ContainerEntry ) {
					entryList.addAll( getParameterEntries( ((ContainerEntry)se).getRefContainer() ) );
				}
			}
		}
		return entryList;
	}
	
	@Override
	public void addTreeModelListener(TreeModelListener l) {
		listeners.add( l );
	}
	
	@Override
	public void removeTreeModelListener(TreeModelListener l) {
		listeners.remove( l );
	}
	
	@Override
	public Object getChild(Object parent, int index) {
		if( parent == root ) {
			return visibleContainers.get(index);
		}
		if ( parent instanceof SequenceContainer ) {
			return visibleEntries.get(parent).get(index);
		}
		return null;
	}

	@Override
	public int getChildCount(Object parent) {
		if( parent == root ) {
			return visibleContainers.size();
		}		
		if ( parent instanceof SequenceContainer ) {
			return visibleEntries.get(parent).size();
		}
		return 0;
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		if( parent == root ) {
			return visibleContainers.indexOf( child );
		} else if ( parent instanceof SequenceContainer ) {
			return visibleEntries.get(parent).indexOf( child );
		}
		return -1;
	}

	@Override
	public Object getRoot() {
		if( root == null ) {
			if( xtcedb != null ) {
				root = xtcedb.getRootSpaceSystem();
			}
			createCache();
		}
		return root;
	}

	@Override
	public boolean isLeaf(Object node) {
		return ( node instanceof ParameterEntry );
	}

	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {
		// Not editable
	}
}

/**
 * Extends base class by adding ability to dynamically include or exclude
 * specific objects.
 * 
 * Assumes that a SequenceContainer is visible if any of its ParameterEntry
 * instances are visible, and that the root is always visible.
 * 
 * Specify always shown objects directly (setAlwaysShown), never shown objects
 * (setNeverShown), or use a search string which is matched against the
 * opsname of the object.
 * 
 * @author atu
 *
 */
class FilterableXtceDbTreeModel extends XtceDbTreeModel {
	private List<String> filterTerms;
	private List<Object> alwaysShown;
	private List<Object> neverShown;
	public boolean allParamsVisibleIfParentVisible = true;

	public FilterableXtceDbTreeModel(XtceDb db) {
		super(db);
		filterTerms = new ArrayList<String>();
		alwaysShown = new ArrayList<Object>();
		neverShown = new ArrayList<Object>();
	}
	
	protected void createCache() {
		visibleContainers.clear();
		visibleEntries.clear();
		if( xtcedb == null ) {
			return;
		}
		for( SequenceContainer sc : xtcedb.getSequenceContainers() ) {
			visibleEntries.put(sc, new ArrayList<ParameterEntry>() );
			boolean addContainer = isVisible( sc );
			for( ParameterEntry pe : getParameterEntries( sc ) ) {
				if( (allParamsVisibleIfParentVisible && addContainer) || isVisible( pe ) ) {
					addContainer = true;
					visibleEntries.get(sc).add(pe);
				}
			}
			if( addContainer ) {
				visibleContainers.add( sc );
			}
		}
		Collections.sort( visibleContainers, new OpsNameComparator() );
	}
	
	public void setFilterText( String filter ) {
		filterTerms.clear();
		for( String term : filter.toLowerCase().split( "\\*" ) ) {
			filterTerms.add( term );
		}
		createCache();
		signalTreeStructureChanged();
	}
	
	private String getFilterableText( Object o ) {
		String name = null;
		if( o instanceof SequenceContainer ) {
			name = ((SequenceContainer)o).getOpsName().toLowerCase();
		} else if ( o instanceof ParameterEntry ) {
			name = ((ParameterEntry)o).getParameter().getOpsName().toLowerCase();
		} else {
			name = o.toString().toLowerCase();
		}
		return name;
	}
	
	protected boolean isVisible( Object o ) {
		if( o == root ) {
			return true;
		}
		if( alwaysShown.contains( o ) ) {
			return true;
		}
		if( neverShown.contains( o ) ) {
			return false;
		}
		String haystack = getFilterableText(o);
		for( String needle : filterTerms ) {
			if( ! haystack.contains( needle ) ) {
				return false;
			}
		}
		return true;
	}
	
	public List<Object> getAlwaysShown() {
		return alwaysShown;
	}

	public void setAlwaysShown(List<Object> alwaysShown) {
		this.alwaysShown = alwaysShown;
		createCache();
	}

	public List<Object> getNeverShown() {
		return neverShown;
	}

	public void setNeverShown(List<Object> neverShown) {
		this.neverShown = neverShown;
		createCache();
	}
	
	private void signalTreeStructureChanged() {
		for( TreeModelListener l : listeners ) {
			l.treeStructureChanged( new TreeModelEvent(this, new Object[]{root}) );
		}
	}
}

class OpsNameComparator implements Comparator<SequenceContainer> {
	@Override
	public int compare( SequenceContainer o1, SequenceContainer o2 ) {
		return o1.getOpsName().compareTo( o2.getOpsName() );
	}
}

/**
 * Displays OpsNames for Xtce objects, and the raw type of ParameterEntry
 * instances in a tooltip.
 * 
 * @author atu
 */
class XtceDbCellRenderer extends DefaultTreeCellRenderer {
	private static final long serialVersionUID = 201211201034L;

	public Component getTreeCellRendererComponent(JTree tree, Object value,
			boolean selected, boolean expanded, boolean leaf, int row,
			boolean hasFocus) {
		String tooltipBody = null;
		if( value instanceof SpaceSystem ) {
			super.getTreeCellRendererComponent(tree, ((SpaceSystem)value).getOpsName(), selected, expanded, leaf, row, hasFocus);
			tooltipBody = ( ((SpaceSystem)value).getQualifiedName()+"<br />"+((SpaceSystem)value).getHeader() );
		} else if ( value instanceof SequenceContainer ) {
			super.getTreeCellRendererComponent(tree, ((SequenceContainer)value).getOpsName(), selected, expanded, leaf, row, hasFocus);
			tooltipBody = ((SequenceContainer)value).getName();
			if( ((SequenceContainer)value).getAliasSet() != null ) {
				tooltipBody += "<br />"+((SequenceContainer)value).getAliasSet().toString();
			}
		} else if ( value instanceof ParameterEntry ) {
			super.getTreeCellRendererComponent(tree, ((ParameterEntry)value).getParameter().getOpsName(), selected, expanded, leaf, row, hasFocus);
			Parameter p = ((ParameterEntry)value).getParameter();
			tooltipBody = "Type: "+p.getParameterType().toString().replaceFirst( " encoding:", "<br />Encoding:" );
		} else if ( value instanceof ContainerEntry ) {
			super.getTreeCellRendererComponent(tree, "("+((ContainerEntry)value).getLocationInContainerInBits()+") "+((ContainerEntry)value).getRefContainer().getOpsName(), selected, expanded, leaf, row, hasFocus);
			tooltipBody = ((ContainerEntry)value).getRefContainer().getOpsName();
		} else {
			super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
		}
		if( tooltipBody != null ) {
			setToolTipText( "<html>"+tooltipBody+"</html>" );
		}
			
		return this;
	}
}