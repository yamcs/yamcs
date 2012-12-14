package org.yamcs.xtce;

import java.io.PrintStream;

import org.yamcs.commanding.TcPacketDefinition;

/**
 * A type definition used as the base type for a CommandDefinition
 * 
 * Currently the XTCE is not followed and instead we just link to the old CD-MCS MDB based command definition.
 * 
 * @author nm
 *
 */
public class MetaCommand extends NameDescription {
    private static final long serialVersionUID = 1L;
    TcPacketDefinition tcPacket;
    
    public MetaCommand(String name) {
        super(name);
    }

    public TcPacketDefinition getTcPacket() {
        return tcPacket;
    }

    public void setTcPacket(TcPacketDefinition tcPacket) {
        this.tcPacket = tcPacket;
    }

    public void print(PrintStream out) {
        out.print("MetaCommand name: "+name);
        if(getAliasSet()!=null) out.print(", aliases: "+getAliasSet());
        out.println( "." );
        out.println( tcPacket );
    }
    
    
}
