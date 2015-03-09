package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ChannelException;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.cmdhistory.CommandHistoryFilter;
import org.yamcs.cmdhistory.CommandHistoryRequestManager;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.Value;

/**
 * Provides realtime command history subscription via web.
 */
public class CommandHistoryClient implements CommandHistoryConsumer {
	Channel channel;
	Logger log;
	WebSocketServerHandler wsHandler;
	int subscriptionId=-1;

	public CommandHistoryClient(Channel channel, WebSocketServerHandler wsHandler) {
		this.channel = channel;
		this.wsHandler = wsHandler;
		log = LoggerFactory.getLogger(CommandHistoryClient.class.getName() + "[" + channel.getInstance() + "]");
	}

	/**
	 * called when the cmdhistory subscribe is received via websocket
	 */
	public void subcribe() {
		CommandHistoryRequestManager chrm = channel.getCommandHistoryManager();
		subscriptionId = chrm.subscribeCommandHistory(null, 0, this);
		
	}
	/**
	 * called when the socket is closed
	 */
	public void quit() {
		if(subscriptionId == -1) return;
		CommandHistoryRequestManager chrm = channel.getCommandHistoryManager();
		chrm.unsubscribeCommandHistory(subscriptionId);
	}


	public void switchChannel(Channel c) throws ChannelException {
		if(subscriptionId == -1) return;

		CommandHistoryRequestManager chrm = channel.getCommandHistoryManager();
		CommandHistoryFilter filter = chrm.unsubscribeCommandHistory(subscriptionId);

		this.channel = c;

		chrm = channel.getCommandHistoryManager();
		chrm.addSubscription(filter, this);
	}

	@Override
	public void addedCommand(PreparedCommand pc) {
		CommandHistoryEntry entry = CommandHistoryEntry.newBuilder().setCommandId(pc.getCommandId()).addAllAttr(pc.getAttirbutes()).build();
		doSend(entry);
	}

	@Override
	public void updatedCommand(CommandId cmdId, long changeDate, String key, Value value) {
		CommandHistoryAttribute cha = CommandHistoryAttribute.newBuilder().setName(key).setValue(value).build();
		CommandHistoryEntry entry = CommandHistoryEntry.newBuilder().setCommandId(cmdId).addAttr(cha).build();
		doSend(entry);
	}


	private void doSend(CommandHistoryEntry entry) {
		try {
			wsHandler.sendData(ProtoDataType.CMD_HISTORY, entry, SchemaCommanding.CommandHistoryEntry.WRITE);
		} catch (Exception e) {
			log.warn("got error when sending command history updates, quitting", e);
			quit();
		}
	}

	
}
