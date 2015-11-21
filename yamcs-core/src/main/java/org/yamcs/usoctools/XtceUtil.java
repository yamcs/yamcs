package org.yamcs.usoctools;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

@Deprecated 
/**
 * Maps between packetid/apid and SequenceContainers.
 * 
 * Is deprecated because no such mappings exist when using not ccsds data 
 *
 */
public class XtceUtil {
    HashMap<Long,SequenceContainer>apidPacketid2Name=new HashMap<Long,SequenceContainer>();
    HashMap<Integer,SequenceContainer>packetid2Name=new HashMap<Integer,SequenceContainer>();

    HashMap<String,Map<String,Integer>>name2Packetid=new HashMap<String,Map<String,Integer>>();

    HashMap<String,Map<String,Long>> name2ApidPacketid=new HashMap<String,Map<String,Long>>();


    HashMap<Long,String>responsePackets=new HashMap<Long,String>();
    HashSet<Integer>apids=new HashSet<Integer>();

    private final XtceDb xtcedb;
    static Map<XtceDb, XtceUtil> instances=new HashMap<XtceDb, XtceUtil>();

    private XtceUtil(XtceDb xtcedb) {
        this.xtcedb=xtcedb;
        buildApidPacketIdMap();
    }

    public synchronized static XtceUtil getInstance(XtceDb xtcedb) {
        XtceUtil x=instances.get(xtcedb);
        if(x==null) {
            x=new XtceUtil(xtcedb);
            instances.put(xtcedb, x);
        }
        return x;
    }

    private void buildApidPacketIdMap() {
        for (SequenceContainer sc:xtcedb.getSequenceContainers()) {
            int apid=-1;
            int packetid=-1;
            MatchCriteria crit = sc.getRestrictionCriteria();
            if (crit instanceof ComparisonList) {
                ComparisonList clist = (ComparisonList)crit;
                for (Comparison comp:clist.getComparisonList()) {
                    Parameter parameter = comp.getParameter();
                    String name = parameter.getName();
                    if (name.equals("ccsds-apid")) {
                        apid = ((Number)comp.getValue()).intValue();
                    }
                    if (name.equals("col-packet_id") || name.equals("packet-id")) {
                        packetid = ((Number)comp.getValue()).intValue();
                    }
                }
            }
            //	System.out.println("sc:"+sc.getName()+" apid:"+apid+" packetid:"+packetid);
            if(packetid!=-1) {
                if((apid!=-1)) {
                    apids.add(apid);
                    long ap=(((long)apid)<<32)+packetid;
                    if("ccsds-response_packet".equals(sc.getBaseContainer().getName())) {
                        responsePackets.put(ap,sc.getName());
                    } else {
                        apidPacketid2Name.put(ap,sc);
                        Map<String,Long> n2ap=name2ApidPacketid.get(null);
                        if(n2ap==null) {
                            n2ap=new HashMap<String,Long>();
                            name2ApidPacketid.put(null, n2ap);
                        }
                        n2ap.put(sc.getName(), ap);
                        for(String ns:sc.getAliasSet().getNamespaces()) {
                            n2ap=name2ApidPacketid.get(ns);
                            if(n2ap==null) {
                                n2ap=new HashMap<String,Long>();
                                name2ApidPacketid.put(ns, n2ap);
                            }
                            n2ap.put(sc.getAlias(ns), ap);
                        } 
                    }
                }
                packetid2Name.put(packetid, sc);
                Map<String,Integer> n2p=name2Packetid.get(null);
                if(n2p==null) {
                    n2p=new HashMap<String,Integer>();
                    name2Packetid.put(null, n2p);
                }
                n2p.put(sc.getName(), packetid);
                for(String ns:sc.getAliasSet().getNamespaces()) {
                    n2p=name2Packetid.get(ns);
                    if(n2p==null) {
                        n2p=new HashMap<String,Integer>();
                        name2Packetid.put(ns, n2p);
                    }
                    n2p.put(sc.getAlias(ns), packetid);
                }

            }
        }
    }

    public String getPacketNameByApidPacketid(int apid, int packetId, String namespace) {
        long ap=(((long)apid)<<32)+packetId;
        if(responsePackets.containsKey(ap)) {
            return responsePackets.get(ap);
        }
        SequenceContainer sc=apidPacketid2Name.get(ap);
        if(sc==null) return null;
        if(namespace==null)return sc.getName();
        else return sc.getAlias(namespace);
    }

    /**
     * this is to be used only if the previous method returns null.
     * the xtce files produced by CD-MCS are bogus: they identify packets based on packetId only which is not unique
     */
    public String getPacketNameByPacketId(int packetId, String namespace) {
        SequenceContainer sc=packetid2Name.get(packetId);
        if(sc==null) return null;
        if(namespace==null)return sc.getName();
        else return sc.getAlias(namespace);
    }

    public Set<Integer> getTmPacketApids() {
        return apids;
    }

    public boolean isResponsePacket(int apid, int packetId) {
        long ap=(((long)apid)<<32)+packetId;
        if(responsePackets.containsKey(ap)) return true;
        else return false;
    }

    /**
     * 
     * @param name - the name of the packet (in the "default" namespace)
     * @return
     */
    public Long getApidPacketId(String name) {
        return name2ApidPacketid.get(null).get(name);
    }

    public Long getApidPacketId(String name, String namespace) {
        Map<String,Long> n2ap=name2ApidPacketid.get(namespace);
        if(n2ap==null)return null;
        return n2ap.get(name);
    }

    public Integer getPacketId(String name, String namespace) {
        Map<String,Integer> n2p=name2Packetid.get(namespace);
        if(n2p==null)return null;
        return n2p.get(name);

    }

    public Integer getPacketId(String name) {
        return name2Packetid.get(null).get(name);
    }



    public Collection<String> getTmPacketNames(String namespace) {
        ArrayList<String> pn=new ArrayList<String>();
        for(SequenceContainer sc:xtcedb.getSequenceContainers()){
            String alias=sc.getAlias(namespace);
            if(alias!=null) pn.add(alias);
        }
        return pn;
    }

    public Collection<String> getResponsePacketNames() {
        return responsePackets.values();
    }

    private void print(PrintStream out) {
        out.println("apidPacketid2Name: ");
        for(Entry<Long,SequenceContainer> entry:apidPacketid2Name.entrySet()) {
            long ap=entry.getKey();
            int apid=(int)(ap>>32);
            int packetid=(int)(ap&0xFFFFFFFF);
            out.println("    ("+apid+", "+packetid+"): "+entry.getValue().getQualifiedName());     
        }

    }
    public static void main(String[] args) throws ConfigurationException {
        YConfiguration.setup();
        XtceDb db=XtceDbFactory.createInstance("erasmus-busoc");
        XtceUtil xtceutil=XtceUtil.getInstance(db);
        xtceutil.print(System.out);
    }
}