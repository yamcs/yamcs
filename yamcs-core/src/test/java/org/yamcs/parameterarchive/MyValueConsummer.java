package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.yamcs.parameter.Value;

class MyValueConsummer implements Consumer<TimedValue> {
    ArrayList<Long> times = new ArrayList<>();
    ArrayList<Value> values = new ArrayList<>();

    @Override
    public void accept(TimedValue tv) {
        times.add(tv.instant);
        values.add(tv.engValue);
    }

}