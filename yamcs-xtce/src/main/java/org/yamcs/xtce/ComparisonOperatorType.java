package org.yamcs.xtce;

import java.io.Serializable;

public class ComparisonOperatorType implements Serializable{
	private static final long serialVersionUID = 200706141154L;
	
	enum Type { EQUALITY, INEQUALITY, LARGERTHAN, LARGEROREQUALTHAN, SMALLERTHAN, SMALLEROREQUALTHAN }
	Type type;

	ComparisonOperatorType(String type) throws IllegalArgumentException {
		if (type.equals("==")) {
			this.type = Type.EQUALITY;
		} else {
			if (type.equals("!=")) {
				this.type = Type.INEQUALITY;
			} else {
				if (type.equals(">")) {
					this.type = Type.LARGERTHAN;
				} else {
					if (type.equals(">=")) {
						this.type = Type.LARGEROREQUALTHAN;
					} else {
						if (type.equals("<")) {
							this.type = Type.SMALLERTHAN;
						} else {
							if (type.equals("<=")) {
								this.type = Type.SMALLEROREQUALTHAN;
							} else {
								throw new IllegalArgumentException("the following type is not a valid ComparisonOperatorType: " + type);
							}
						}
					}
				}
			}
		}
	}

	public boolean apply(long value1, long value2) {
		switch (this.type) {
			case EQUALITY: {
				return (value1 == value2);
			}
			case INEQUALITY: {
				return (value1 != value2);
			}
			case LARGERTHAN: {
				return (value1 > value2);
			}
			case LARGEROREQUALTHAN: {
				return (value1 >= value2);
			}
			case SMALLERTHAN: {
				return (value1 < value2);
		    }	
			case SMALLEROREQUALTHAN: {
				return (value1 <= value2);
			}
		}
		return true; // should never be reached
	}

	public String value() {
		switch (this.type) {
			case EQUALITY: {
				return "==";
			}
			case INEQUALITY: {
				return "!=";
			}
			case LARGERTHAN: {
				return ">";
			}
			case LARGEROREQUALTHAN: {
				return ">=";
			}
			case SMALLERTHAN: {
				return "<";
			}
			case SMALLEROREQUALTHAN: {
				return "<=";
			}
		}
		return ""; // should never be reached
	}
}

