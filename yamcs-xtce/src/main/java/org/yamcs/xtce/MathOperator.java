package org.yamcs.xtce;

/**
 * Mathematical operators used in the math operation. Behaviour of each operator on the stack is described using
 * notation (before -- after), where "before" represents the stack before execution of the operator and "after"
 * represent the stack after execution.
 * <p>
 * The top of the stack is at the right, e.g. in (x1 x2 -- x1-x2), x2 is the element on the top, x1 is the second from
 * the top and (x1-x2) is the top of the frame after the execution.
 *
 * @author nm
 *
 */
public enum MathOperator {
    /**
     * addition (x1 x2 -- x1+x2)
     */
    PLUS("+", 2),
    /**
     * subtraction (x1 x2 -- x1-x2)
     */
    MINUS("-", 2),
    /**
     * multiplication (x1 x2 -- x1*x2)
     */
    STAR("*", 2),
    /**
     * division (x1 x2 -- x1/x2) An undefined condition exists if x2 is zero
     */
    DIV("/", 2),
    /**
     * unsigned mod (x1 x2 -- x3) Divide x1 by x2, giving the remainder x3; an undefined condition exists if x2 is zero
     */
    MODULO("%", 2),
    /**
     * power function (x1 x2 -- x1**x2)
     */
    POW("^", 2),
    /**
     * reverse power function (x1 x2 -- x2**x1)
     */
    REVPOW("y^x", 2),
    /**
     * natural (base e) logarithm (x -- ln(x)) An undefined condition exists if x is less than or equal to zero
     */
    LN("ln", 1),
    /**
     * base-10 logarithm (x-- log(x)) An undefined condition exists if x is less than or equal to zero
     */
    LOG("log", 1),
    /**
     * exponentiation (x -- exp(x))
     */
    EXP("e^x", 1),
    /**
     * inversion (x -- 1/x) An undefined condition exists if x is zero
     */
    INV("1/x", 1),
    /**
     * factorial (x -- x!) An undefined condition exists if x is less than zero
     */
    FACT("x!", 1),
    /**
     * tangent (x -- tan\(x)) radians
     */
    TAN("tan", 1),
    /**
     * cosine (x -- cos\(x)) radians
     */
    COS("cos", 1),
    /**
     * sine (x -- sin\(x)) radians
     */
    SIN("sin", 1),
    /**
     * arctangent (x -- atan\(x)) radians
     */
    ATAN("atan", 1),
    /**
     * arccosine (x -- acos\(x)) radians
     */
    ACOS("acos", 1),
    /**
     * arcsine (x -- asin\(x)) radians
     */
    ASIN("asin", 1),
    /**
     * hyperbolic tangent (x -- tanh(x))
     */
    TANH("tanh", 1),
    /**
     * hyperbolic cosine (x -- cosh\(x))
     */
    COSH("cosh", 1),
    /**
     * hyperbolic sine (x -- sinh\(x))
     */
    SINH("sinh", 1),
    /**
     * hyperbolic arctangent (x -- atanh\(x))
     * <p>
     * An undefined condition exists if x is outside the range [-1.0,+1.0]
     */
    ATANH("atanh", 1),
    /**
     * hyperbolic arccosine (x -- acosh\(x))
     * <p>
     * An undefined condition exists if n is less than one
     */
    ACOSH("acosh", 1),
    /**
     * hyperbolic arcsine (x -- asinh\(x))
     */
    ASINH("asinh", 1),
    /**
     * swap the top two stack items (x1 x2 -- x2 x1)
     */
    SWAP("swap", 2),
    /**
     * Remove top item from the stack (x -- )
     */
    DROP("drop", 1),
    /**
     * Duplicate top item on the stack (x -- x x)
     */
    DUP("dup", 1),
    /**
     * Duplicate second item to the top of the stack (x1 x2 -- x1 x2 x1)
     */
    OVER("over", 1),
    /**
     * absolute value (x1 -- abs(x1))
     */
    ABS("abs", 1),
    /**
     * bitwise right shift (x1 x2 -- x1 >> x2)
     */
    LEFT_SHIFT("<<", 2),
    /**
     * bitwise left shift (x1 x2 -- x1 << x2)
     */
    RIGHT_SHIFT(">>", 2),
    /**
     * bitwise or (x1 x2 -- x1 | x2)
     */
    BITWISE_OR("|", 2),
    /**
     * bitwise and (x1 x2 -- x1 & x2)
     */
    BITWISE_AND("&", 2);
    
    private final String xtceName;
    private final int arity;
    
    MathOperator(String v, int arity) {
        this.xtceName = v;
        this.arity = arity;
    }
    public int getArity() {
        return arity;
    }
    public static MathOperator fromXtceName(String s) {
        for(MathOperator mo: values()) {
            if(mo.xtceName.equals(s)) {
                return mo;
            }
        }
        throw new IllegalArgumentException("Invalid math operator '"+s+"'");
    }
    public String xtceName() {
        return xtceName;
    }
   
}
