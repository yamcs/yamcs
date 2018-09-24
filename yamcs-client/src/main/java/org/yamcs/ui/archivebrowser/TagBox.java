package org.yamcs.ui.archivebrowser;

import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class TagBox extends Box implements MouseListener {
    private static final long serialVersionUID = 1L;
    private DataView dataView;
    boolean drawPreviewLocator;
    long startLocator, stopLocator, currentLocator;

    final long DO_NOT_DRAW = Long.MIN_VALUE;

    JLabel tagLabelItem;
    JPopupMenu editTagPopup, newTagPopup;
    JMenuItem removeTagMenuItem, editTagMenuItem;
    int selectedRow=-1, selectedIndex=-1;

    List<List<ArchiveTag>> tags=new ArrayList<List<ArchiveTag>>();//all tags loaded from yarch
    
    TagEditDialog tagEditDialog;
    ZoomSpec zoom;
    
    
    private void buildTagEditDialog() {
        if(tagEditDialog==null) {
            tagEditDialog=new TagEditDialog(null);
            tagEditDialog.setModal(true);
        }
    }

    TagBox(DataView dataView) {
        super(BoxLayout.PAGE_AXIS);
        this.dataView = dataView;

        startLocator = stopLocator = currentLocator = DO_NOT_DRAW;
        drawPreviewLocator = false;
        setOpaque(false);

        ToolTipManager ttmgr = ToolTipManager.sharedInstance();
        ttmgr.setInitialDelay(0);
        ttmgr.setReshowDelay(0);
        ttmgr.setDismissDelay(Integer.MAX_VALUE);

        buildPopup();
        addMouseListener(this);
        /*
        insertTag(ArchiveTag.newBuilder().setName("plus infinity").setStart(450).build());
        insertTag(ArchiveTag.newBuilder().setName("cucucurigo long laaaaaaabel").setStart(100).setStop(300).setColor("red").build());
        insertTag(ArchiveTag.newBuilder().setName("tag2").setStart(TimeEncoding.parse("2009-04-15T05:18:00")).setStop(TimeEncoding.parse("2009-06-03T18:40:37")).setColor("blue").build());
        insertTag(ArchiveTag.newBuilder().setName("minus infinity").setStop(150).build());
        insertTag(ArchiveTag.newBuilder().setName("plus infinity").setStart(450).build());
        //    insertTag(ArchiveTag.newBuilder().setName("plus infinity").setStart(450).build());
        insertTag(ArchiveTag.newBuilder().setName("tag3").setStart(TimeEncoding.parse("2009-07-07T12:29:44")).setStop(TimeEncoding.parse("2009-07-07T13:28:26")).setColor("orange").build());
        */
    }

    /**
     * insert a tag in tags, in order ensuring no overlap.
     * @param tag
     */
    private void insertTag(ArchiveTag tag) {
        boolean inserted=false;
        for(List<ArchiveTag> atl:tags) {
            int min=0, max=atl.size()-1;
            while(min<=max) {
                int mid=(min+max)>>1;
            ArchiveTag midtag=atl.get(mid);
            if(tag.hasStop() && midtag.hasStart() && tag.getStop()<midtag.getStart()) {
                max=mid-1;
            } else if(tag.hasStart() && midtag.hasStop() && tag.getStart()>midtag.getStop()) {
                min=mid+1;
            } else {
                break; //overlap
            }
            }
            if(min>max) {
                atl.add(min,tag);
                inserted=true;
                break;
            }
        }
        if(!inserted) {
            List<ArchiveTag> atl=new ArrayList<ArchiveTag>();
            atl.add(tag);
            tags.add(atl);
        }
    }

    protected void buildPopup() {
        editTagPopup = new JPopupMenu();
        tagLabelItem = new JLabel();
        tagLabelItem.setEnabled(false);
        Box hbox = Box.createHorizontalBox();
        hbox.add(Box.createHorizontalGlue());
        hbox.add(tagLabelItem);
        hbox.add(Box.createHorizontalGlue());
        editTagPopup.insert(hbox, 0);
        editTagPopup.addSeparator();
        editTagMenuItem = new JMenuItem("Edit Tag");
        editTagMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                buildTagEditDialog();
                ArchiveTag selectedTag=tags.get(selectedRow).get(selectedIndex);
                tagEditDialog.fillFrom(selectedTag);
                tagEditDialog.setVisible(true);
                if(tagEditDialog.ok) {
                    dataView.emitActionEvent(new TagEvent(this, "update-tag", selectedTag, tagEditDialog.getTag()));
                }
            }
        });
        editTagPopup.add(editTagMenuItem);

        removeTagMenuItem = new JMenuItem("Remove Tag");
        removeTagMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ArchiveTag selectedTag=tags.get(selectedRow).get(selectedIndex);
                int answer=JOptionPane.showConfirmDialog(null, "Remove "+selectedTag.getName()+" ?", "Are you sure?", JOptionPane.YES_NO_OPTION);
                if(answer==JOptionPane.YES_OPTION) {
                    dataView.emitActionEvent(new TagEvent(this, "delete-tag", selectedTag, null));
                }
            }
        });
        editTagPopup.add(removeTagMenuItem);

        newTagPopup = new JPopupMenu();
        JMenuItem newTagMenuItem = new JMenuItem("New Tag");
        newTagMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                buildTagEditDialog();
                tagEditDialog.setVisible(true);
                if(tagEditDialog.ok) {
                    dataView.emitActionEvent(new TagEvent(this, "insert-tag", null, tagEditDialog.getTag()));
                }
            }
        });
        newTagPopup.add(newTagMenuItem);
    }

    public void createNewTag(long start, long stop) {
        buildTagEditDialog();
        tagEditDialog.startTextField.setValue(start);
        tagEditDialog.stopTextField.setValue(stop);
        tagEditDialog.setVisible(true);

        if(tagEditDialog.ok) {
            dataView.emitActionEvent(new TagEvent(this, "insert-tag", null, tagEditDialog.getTag()));
        }
    }
    
    public void doMousePressed(MouseEvent e, int row, int index) {
        selectedRow=row;
        selectedIndex=index;
        if(e.isPopupTrigger()) {
            showPopup(e);
        } else if(e.getButton()==MouseEvent.BUTTON1 && selectedRow!=-1 && selectedIndex !=-1) {
            dataView.selectedTag(tags.get(selectedRow).get(selectedIndex));
        }
    }

    void showPopup(final MouseEvent e) {
        if(selectedIndex!=-1) {
            ArchiveTag selectedTag=tags.get(selectedRow).get(selectedIndex);
            tagLabelItem.setText(selectedTag.getName());
            editTagPopup.validate();
            editTagPopup.show(e.getComponent(), e.getX(), e.getY());
        } else {
            newTagPopup.validate();
            newTagPopup.show(e.getComponent(), e.getX(), e.getY());
        }
    }
    /*
    void hidePopup(final MouseEvent e) {
        if(true)return;
        if (e.isPopupTrigger()) {
            if ((packetPopup != null) && (popupLabelItem != null)) {
                popupLabelItem.setVisible(false);
                removePayloadMenuItem.setVisible(false);
                removePacketMenuItem.setVisible(false);
                removeExceptPacketMenuItem.setVisible(false);
                copyOpsnameMenuItem.setVisible(false);
                changeColorMenuItem.setVisible(false);
                packetPopup.validate();
                packetPopup.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }
     */


    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return new Point(event.getX() - 94, event.getY() + 20);
    }
    
    void setToZoom(ZoomSpec zoom) {
        this.zoom=zoom;
        redrawTags();
    }
    
    void redrawTags() { // Draw reverse, so that 'most' tags stick to scale
        removeAll();
        if(!tags.isEmpty()) {
            int row=tags.size()-1;
            Insets in=this.getInsets();
            for(ListIterator<List<ArchiveTag>> it=tags.listIterator(tags.size()); it.hasPrevious();) {
                List<ArchiveTag> lat=it.previous();
                TagTimeline tt=new TagTimeline(this, lat, zoom, row--, in.left);
                add(tt);
            }
        }
        revalidate();
        repaint();
    }
    @Override
    public void mousePressed(MouseEvent e) {
        doMousePressed(e, -1, -1);
    }

    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    
    static public void main(String[] args) {
        TimeEncoding.setUp();
        JFrame frame=new JFrame();
        frame.setSize(new Dimension(1000,100));
        TagBox atb=new TagBox(null);
    /*    atb.insertTag(ArchiveTag.newBuilder().setName("plus infinity").setStart(450).build());
        atb.insertTag(ArchiveTag.newBuilder().setName("cucucurigo long laaaaaaabel").setStart(100).setStop(300).setColor("red").build());
        atb.insertTag(ArchiveTag.newBuilder().setName("tag2").setStart(500).setStop(700).setColor("blue").build());

        atb.insertTag(ArchiveTag.newBuilder().setName("minus infinity").setStop(150).build());
        atb.insertTag(ArchiveTag.newBuilder().setName("plus infinity").setStart(450).build());
        atb.insertTag(ArchiveTag.newBuilder().setName("tag3").setStart(701).setStop(800).setColor("orange").build());
        atb.insertTag(ArchiveTag.newBuilder().setName("tag4").setStart(800).setStop(900).setColor("magenta").build());
        atb.insertTag(ArchiveTag.newBuilder().setName("tag5").setStart(801).setStop(1000).setColor("gray").build());
*/
        atb.setToZoom(new ZoomSpec(0, 3600*1000, 3600*1000, 3*3600*1000));
        frame.add(atb);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    public void addTags(List<ArchiveTag> tagList) {
       for(ArchiveTag tag:tagList) {
           insertTag(tag);
       }
       if (!dataView.zoomStack.empty()) {
           redrawTags();
       }
    }

    public void addTag(ArchiveTag tag) {
        insertTag(tag);
        redrawTags();
    }

    public void removeTag(ArchiveTag rtag) {
        long t=rtag.hasStart()?rtag.getStart():rtag.getStop();  
        for(List<ArchiveTag> tagList:tags) {
            int id=TagTimeline.time2Tag(tagList, t);
            if(id!=-1) {
                if(rtag.equals(tagList.get(id))) {
                    tagList.remove(id);
                    if(tagList.isEmpty()) tags.remove(tagList);
                    redrawTags();
                    return;
                }
            }
        }
        JOptionPane.showMessageDialog(null, "Could not find  "+rtag.toString()+" to remove");
    }
    

    public void updateTag(ArchiveTag oldTag, ArchiveTag newTag) {
        long t=oldTag.hasStart()?oldTag.getStart():oldTag.getStop();  
        for(List<ArchiveTag> tagList:tags) {
            int id=TagTimeline.time2Tag(tagList, t);
            if(id!=-1) {
                if(oldTag.equals(tagList.get(id))) {
                    tagList.remove(id);
                    insertTag(newTag);
                    redrawTags();
                    return;
                }
            }
        }
        JOptionPane.showMessageDialog(null, "Could not find  "+oldTag.toString()+" to remove");
    }
    static public class TagEvent extends ActionEvent {
        public ArchiveTag newTag;
        public ArchiveTag oldTag;
        
        public TagEvent(Object source, String command, ArchiveTag oldTag, ArchiveTag newTag) {
            super(source, ActionEvent.ACTION_PERFORMED, command);
            this.newTag=newTag;
            this.oldTag=oldTag;
        }
    }
}

