package org.yamcs.yarch.streamsql;

import java.math.BigDecimal;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class WindowSpecification {
	final String name; //in case it refers to an existing window
	public enum Type {TIME, TUPLES, FIELD};
	public final Type type;
	public final BigDecimal size, advance;
	public final String field;
	DataType fieldType;
	
	public WindowSpecification(String name) {
		this.name=name;	
		advance=size=null;
		type=null;
		field=null;
	}
	public WindowSpecification(BigDecimal size, BigDecimal advance, Type type) {
		this.name=null;
		this.type=type;
		this.size=size;
		this.advance=advance;
		this.field=null;
	}
	public WindowSpecification(BigDecimal size, BigDecimal advance, Type type, String field) {
		this.name=null;
		this.type=type;
		this.size=size;
		this.advance=advance;
		this.field=field;
	}
	public void bind(TupleDefinition inputDef) throws StreamSqlException {
		switch(type) {
		case FIELD:
			ColumnDefinition cd=inputDef.getColumn(field);
			if(cd==null) throw new StreamSqlException(ErrCode.COLUMN_NOT_FOUND,"Field '"+field+"' not part of the input");
			fieldType=cd.getType();
			if((fieldType!=DataType.INT) && (fieldType!=DataType.TIMESTAMP)) {
				throw new StreamSqlException(ErrCode.INCOMPATIBLE,"Cannot create windows on fields of type "+cd.getType());
			}
			break;
		case TIME: //TODO
		case TUPLES: //TODO
			
		}
		
	}
	
	public DataType getFieldType(){
	    return fieldType;
	}
}
