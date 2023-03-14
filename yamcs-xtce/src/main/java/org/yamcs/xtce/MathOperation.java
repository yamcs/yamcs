package org.yamcs.xtce;

import java.io.Serializable;
import java.util.List;

/**
 * Postfix (aka Reverse Polish Notation (RPN)) notation is used to describe mathematical equations. It uses a stack
 * where operands (either fixed values or ParameterInstances) are pushed onto the stack from first to last in the XML.
 * As the operators are specified, each pops off operands as it evaluates them, and pushes the result back onto the
 * stack. In this case postfix is used to avoid having to specify parenthesis. To convert from infix to postfix, use
 * Dijkstra's "shunting yard" algorithm.
 * 
 * @author nm
 *
 */
public class MathOperation implements Serializable {
    private static final long serialVersionUID = 1L;
    final private List<Element> elementList;

    public static enum ElementType {
        /**
         * Use a constant in the calculation.
         */
        VALUE_OPERAND("ValueOperand"),
        /**
         * Use the value of this parameter in the calculation. It is the calibrator's value only. If the raw value is
         * needed, specify it explicitly using ParameterInstanceRefOperand. Note this element has no content.
         */
        THIS_PARAMETER_OPERAND("ThisParameterOperand"),
        /**
         * This element is used to reference the last received/assigned value of any Parameter in this math operation.
         */
        PARAMETER_INSTANCE_REF_OPERAND("ParameterInstanceRefOperand"),
        /**
         * All operators utilize operands on the top values in the stack and leaving the result on the top of the stack.
         * Ternary operators utilize the top three operands on the stack, binary operators utilize the top two operands
         * on the stack, and unary operators use the top operand on the stack.
         */
        OPERATOR("Operator");

        final String xtceName;

        ElementType(String xtceName) {
            this.xtceName = xtceName;
        }

        public String xtceName() {
            return xtceName;
        }
    }

    public static class Element implements Serializable {
        private static final long serialVersionUID = 1L;
        final ElementType type;
        double value;
        ParameterInstanceRef pref;
        MathOperator operator;

        public Element(double value) {
            this.type = ElementType.VALUE_OPERAND;
            this.value = value;
        }

        public Element() {
            this.type = ElementType.THIS_PARAMETER_OPERAND;
        }

        public Element(ParameterInstanceRef pref) {
            this.type = ElementType.PARAMETER_INSTANCE_REF_OPERAND;
            this.pref = pref;
        }

        public Element(MathOperator op) {
            this.type = ElementType.OPERATOR;
            this.operator = op;
        }

        public Element(ElementType type) {
            this.type = type;
        }

        public ElementType getType() {
            return type;
        }

        public double getValue() {
            return value;
        }

        public MathOperator getOperator() {
            return operator;
        }

        public ParameterInstanceRef getParameterInstanceRef() {
            return pref;
        }

        @Override
        public String toString() {
            switch (type) {
            case OPERATOR:
                return operator.xtceName();
            case PARAMETER_INSTANCE_REF_OPERAND:
                return "pref";
            case THIS_PARAMETER_OPERAND:
                return "this";
            case VALUE_OPERAND:
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
