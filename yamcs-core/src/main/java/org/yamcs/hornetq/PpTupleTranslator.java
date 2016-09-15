package org.yamcs.hornetq;

import java.security.InvalidParameterException;
import java.util.ArrayList;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.yamcs.tctm.PpProviderAdapter;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterData.Builder;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

/**
 * Translates between Processed Parameter tuples and ActiveMQ messages.
 * @author atu
 */
public class PpTupleTranslator implements TupleTranslator {

    @Override
    public ClientMessage buildMessage( ClientMessage msg, Tuple tuple ) {
        long genTime = (Long)tuple.getColumn(PpProviderAdapter.PP_TUPLE_COL_GENTIME);
        msg.putLongProperty( PpProviderAdapter.PP_TUPLE_COL_GENTIME, genTime);

        String ppGroup =(String)tuple.getColumn(PpProviderAdapter.PP_TUPLE_COL_PPGROUP); 
        msg.putStringProperty( PpProviderAdapter.PP_TUPLE_COL_PPGROUP,  ppGroup);


        msg.putIntProperty( PpProviderAdapter.PP_TUPLE_COL_SEQ_NUM, (Integer)tuple.getColumn(PpProviderAdapter.PP_TUPLE_COL_SEQ_NUM) );
        msg.putLongProperty( PpProviderAdapter.PP_TUPLE_COL_RECTIME, (Long)tuple.getColumn(PpProviderAdapter.PP_TUPLE_COL_RECTIME) );

        Builder b = ParameterData.newBuilder();
        b.setGenerationTime(genTime);
        b.setGroup(ppGroup);
        for( int i = 4; i < tuple.size(); i++ ) {
            // PP name is part of the instance
            ParameterValue ppValue = (ParameterValue)tuple.getColumn(i);
            b.addParameter(ppValue);
        }

        Protocol.encode(msg, b.build());
        return msg;
    }

    @Override
    public Tuple buildTuple( TupleDefinition tdef, ClientMessage message ) {
        final DataType paraDataType=DataType.protobuf(org.yamcs.protobuf.Pvalue.ParameterValue.class.getName());
        Tuple t = null;
        try {
            ParameterData pd = (ParameterData)Protocol.decode(message, ParameterData.newBuilder());
            TupleDefinition tupleDef = tdef.copy();

            ArrayList<Object> columns = new ArrayList<Object>( 4 + pd.getParameterCount() );
            columns.add(message.getLongProperty( PpProviderAdapter.PP_TUPLE_COL_GENTIME ));
            columns.add(message.getStringProperty( PpProviderAdapter.PP_TUPLE_COL_PPGROUP ));
            columns.add(message.getIntProperty( PpProviderAdapter.PP_TUPLE_COL_SEQ_NUM ));
            columns.add(message.getLongProperty( PpProviderAdapter.PP_TUPLE_COL_RECTIME ));

            for( ParameterValue pv : pd.getParameterList() ) {
                String processedParameterName = pv.getId().getName();
                if( processedParameterName == null || "".equals( processedParameterName ) ) {
                    throw new InvalidParameterException( "Processed Parameter must have a name." );
                }
                tupleDef.addColumn( processedParameterName, paraDataType );
                columns.add( pv );
            }

            t = new Tuple(tupleDef, columns);
        } catch( YamcsApiException e ) {
            throw new IllegalArgumentException(e.toString());
        }
        return t;
    }

}
