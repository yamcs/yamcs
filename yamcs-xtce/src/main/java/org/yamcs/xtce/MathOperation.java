package org.yamcs.xtce;

import java.io.Serializable;
import java.util.List;

/**
* Postfix (aka Reverse Polish Notation (RPN)) notation is used to describe mathematical equations. 
* It uses a stack where operands (either fixed values or ParameterInstances) are pushed onto the stack from first to last in the XML. 
* As the operators are specified, each pops off operands as it evaluates them, and pushes the result back onto the stack. 
* In this case postfix is used to avoid having to specify parenthesis. To convert from infix to postfix, use Dijkstra's "shunting yard" algorithm.
* @author nm
*
*/
public class MathOperation implements Serializable {
    private static final long serialVersionUID = 1L;
    final private List<Element> elementList;

    public static enum ElementType {
        ValueOperand, ThisParameterOperand, ParameterInstanceRefOperand, Operator
    }
    
    public static class Element implements Serializable {
        private static final long serialVersionUID = 1L;
        final ElementType type;
        double value;
        ParameterInstanceRef pref;
        MathOperator operator;
        
        public Element(double value) {
            this.type = ElementType.ValueOperand;
            this.value = value;
        }
        
        public Element() {
            this.type = ElementType.ThisParameterOperand;
        }
       
        public Element(ParameterInstanceRef pref) {
            this.type = ElementType.ParameterInstanceRefOperand;
            this.pref = pref;
        }

        public Element(MathOperator op) {
            this.type = ElementType.Operator;
            this.operator = op;
        }

        public Element(ElementType type) {
            this.type = type;
        }

        public ElementType getType() {
            return type;
        }

        public MathOperator getOperator() {
            return operator;
        }
        
        public ParameterInstanceRef getParameterInstanceRef() {
            return pref;
        }
        
        public String toString() {
            switch(type) {
            case Operator:
                return operator.xtceName();
            case ParameterInstanceRefOperand:
                return "pref";
            case ThisParameterOperand:
                return "this";
            case ValueOperand:
                return Double.toString(value);
            }
            return "";
        }

        public void setParameterInstance(ParameterInstanceRef pref) {
            this.pref = pref;
        }
    }
    
    public MathOperation(List<Element> elementList) {
        this.elementList = elementList;
    }

    public List<Element> getElementList() {
        return elementList;
    }

}
