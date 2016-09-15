package org.yamcs;

import java.util.Map;

import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

/*
*@deprecated please use {@link ProcessorCreatorService} instead
*/
public class YProcessorCreatorService extends ProcessorCreatorService {

    public YProcessorCreatorService(String yamcsInstance,
            Map<String, String> config) throws ConfigurationException,
            StreamSqlException, ProcessorException, ParseException {
        super(yamcsInstance, config);
    }

}
