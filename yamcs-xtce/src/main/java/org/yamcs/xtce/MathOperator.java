package org.yamcs.xtce;
/**
 * Mathematical operators used in the math operation.  Behaviour of each operator on the stack is described using notation (before -- after),
 *  where "before" represents the stack before execution of the operator and "after" represent the stack after execution.
 * @author nm
 *
 */
public enum MathOperator {
    PLUS("+", 2), //addition (x1 x2 -- x1+x2)
    MINUS("-", 2), //subtraction (x1 x2 -- x1-x2)
    STAR("*", 2), //multiplication (x1 x2 -- x1*x2)
    DIV("/", 2), //division (x1 x2 -- x1/x2) An undefined condition exists if x2 is zero
    MODULO("%", 2), //unsigned mod (x1 x2 -- x3) Divide x1 by x2, giving the remainder x3; an undefined condition exists if x2 is  zero
    POW("^", 2), //power function (x1 x2 -- x1**x2)
    REVPOW("y^x", 2), //reverse power function (x1 x2 -- x2**x1)
    LN("ln", 1), //natural (base e) logarithm (x -- ln\(x)) An undefined condition exists if x is less than or equal to zero</documentation>
    LOG("log", 1), //base-10 logarithm (x-- log\(x)) An undefined condition exists if x is less than or equal to zero</documentation>
    EXP("e^x", 1), //exponentiation (x -- exp\(x))
    INV("1/x", 1), //inversion (x -- 1/x) An undefined condition exists if x is zero</documentation>
    FACT("x!", 1), //factorial (x -- x!) An undefined condition exists if x is less than zero</documentation>
    TAN("tan", 1), //tangent (x -- tan\(x)) radians</documentation>
    COS("cos", 1), //cosine (x -- cos\(x)) radians</documentation>
    SIN("sin", 1), //sine (x -- sin\(x)) radians</documentation>
    ATAN("atan", 1), //arctangent (x -- atan\(x)) radians</documentation>
    ACOS("acos", 1), //arccosine (x -- acos\(x)) radians</documentation>
    ASIN("asin", 1), //arcsine (x -- asin\(x)) radians</documentation>
    TANH("tanh", 1), //hyperbolic tangent (x -- tanh\(x))</documentation>
    COSH("cosh", 1), //hyperbolic cosine (x -- cosh\(x))</documentation>
    SINH("sinh", 1), //hyperbolic sine (x -- sinh\(x))</documentation>
    ATANH("atanh", 1), //hyperbolic arctangent (x -- atanh\(x)) An undefined condition exists if x is outside the range [-1.0,+1.0]</documentation>
    ACOSH("acosh", 1), //hyperbolic arccosine (x -- acosh\(x)) An undefined condition exists if n is less than one</documentation>
    ASINH("asinh", 1), //hyperbolic arcsine (x -- asinh\(x)) </documentation>
    SWAP("swap", 2), //swap the top two stack items (x1 x2 -- x2 x1)</documentation>
    DROP("drop", 1), //Remove top item from the stack (x -- )</documentation>
    DUP("dup", 1), //Duplicate top item on the stack (x -- x x)</documentation>
    OVER("over", 1), //Duplicate top item on the stack (x1 x2 -- x1 x2 x1)</documentation>
    ABS("abs", 1); //Not sure if ABS is part of the XTCE but it appears in BogusSAT-1
    
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
