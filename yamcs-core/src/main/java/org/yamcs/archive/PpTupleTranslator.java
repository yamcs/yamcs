package org.yamcs.archive;

import java.util.ArrayList;

import org.hornetq.api.core.client.ClientMessage;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterData.Builder;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.hornet.TupleTranslator;

/**
 * Translates between Processed Parameter tuples and HornetQ messages.
 * @author atu
 *
 */
public class PpTupleTranslator implements TupleTranslator {

	@Override
	public ClientMessage buildMessage( ClientMessage msg, Tuple tuple ) {
		msg.putObjectProperty( PpProviderAdapter.PP_TUPLE_COL_GENTIME, tuple.getColumn(PpProviderAdapter.PP_TUPLE_COL_GENTIME) );
		msg.putObjectProperty( PpProviderAdapter.PP_TUPLE_COL_PPGROUP, tuple.getColumn(PpProviderAdapter.PP_TUPLE_COL_PPGROUP) );
		msg.putObjectProperty( PpProviderAdapter.PP_TUPLE_COL_SEQ_NUM, tuple.getColumn(PpProviderAdapter.PP_TUPLE_COL_SEQ_NUM) );
		msg.putObjectProperty( PpProviderAdapter.PP_TUPLE_COL_RECTIME, tuple.getColumn(PpProviderAdapter.PP_TUPLE_COL_RECTIME) );
		
		Builder b = ParameterData.newBuilder();
		for( int i = 4; i < tuple.size(); i++ ) {
			String ppName = tuple.getColumnDefinition(i).getName();
			ParameterValue ppValue = (ParameterValue)tuple.getColumn(i);
			b.addParameter(ppValue);
		}

		Protocol.encode(msg, b.build());
		return msg;
	}

	@Override
	public Tuple buildTuple( TupleDefinition tdef, ClientMessage message ) {
		Tuple t = null;
		try {
			ParameterData pd = (ParameterData)Protocol.decode(message, ParameterData.newBuilder());
			ArrayList<Object> columns = new ArrayList<Object>( 4 + pd.getParameterCount() );
			columns.add(message.getObjectProperty( PpProviderAdapter.PP_TUPLE_COL_GENTIME ));
			columns.add(message.getObjectProperty( PpProviderAdapter.PP_TUPLE_COL_PPGROUP ));
			columns.add(message.getObjectProperty( PpProviderAdapter.PP_TUPLE_COL_SEQ_NUM ));
			columns.add(message.getObjectProperty( PpProviderAdapter.PP_TUPLE_COL_RECTIME ));
			for( ParameterValue pv : pd.getParameterList() ) {
				columns.add( pv );
			}
			t = new Tuple(tdef, columns);
			
		} catch( YamcsApiException e ) {
			throw new IllegalArgumentException(e.toString());
		}
		return t;
	}

}
