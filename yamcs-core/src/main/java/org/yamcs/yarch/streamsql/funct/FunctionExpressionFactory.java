package org.yamcs.yarch.streamsql.funct;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.AggregateListExpression;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.FirstValExpression;

public class FunctionExpressionFactory {
    static Map<String, FunctConfig> functions = new HashMap<>();
    static {
        addFunction("count", CountExpression.class);
        addFunction("sum", SumExpression.class);
        addFunction("aggregatelist", AggregateListExpression.class);
        addFunction("firstval", FirstValExpression.class);
        addFunction("substring", SubstringExpression.class);

        functions.put("extract_short", new FunctConfig(ExtractNumberExpression.class, String.class, "decodeShort"));
        functions.put("extract_ushort",
                new FunctConfig(ExtractNumberExpression.class, String.class, "decodeUnsignedShort"));
        functions.put("extract_int", new FunctConfig(ExtractNumberExpression.class, String.class, "decodeInt"));
        functions.put("extract_u3bytes",
                new FunctConfig(ExtractNumberExpression.class, String.class, "decodeUnsigned3Bytes"));

        addFunction("unhex", UnhexExpression.class);
        addFunction("coalesce", CoalesceExpression.class);
    }

    static void addFunction(String name, Class<? extends Expression> c) {
        functions.put(name, new FunctConfig(c));
    }

    public static Expression get(String name, List<Expression> args, boolean star) throws ParseException {
        Expression[] argsa = (args == null) ? new Expression[0] : args.toArray(new Expression[0]);
        FunctConfig fc = functions.get(name.toLowerCase());
        if (fc == null) {
            throw new ParseException("unknown function '" + name + "'");
        }
        Class<?>[] argTypes = new Class<?>[2 + fc.extraArgTypes.length];
        argTypes[0] = argsa.getClass();
        argTypes[1] = boolean.class;
        System.arraycopy(fc.extraArgTypes, 0, argTypes, 2, fc.extraArgTypes.length);

        Object[] argValss = new Object[2 + fc.extraArgs.length];
        argValss[0] = argsa;
        argValss[1] = star;
        System.arraycopy(fc.extraArgs, 0, argValss, 2, fc.extraArgs.length);

        try {
            return fc.functClass.getConstructor(argTypes).newInstance(argValss);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            throw new ParseException(e.toString());
        }
    }

    static class FunctConfig {
        Class<? extends Expression> functClass;
        Class<?>[] extraArgTypes;
        Object[] extraArgs;

        public FunctConfig(Class<? extends Expression> functClass, Class<?> argType, Object arg) {
            this.functClass = functClass;
            this.extraArgTypes = new Class<?>[] { argType };
            this.extraArgs = new Object[] { arg };
        }

        public FunctConfig(Class<? extends Expression> functClass, Class<?> arg1Type, Object arg1, Class<?> arg2Type,
                Object arg2) {
            this.functClass = functClass;
            this.extraArgTypes = new Class<?>[] { arg1Type, arg2Type };
            this.extraArgs = new Object[] { arg1, arg2 };
        }

        public FunctConfig(Class<? extends Expression> functClass) {
            this.functClass = functClass;
            this.extraArgTypes = new Class<?>[0];
            this.extraArgs = new Object[0];
        }
    }
}
