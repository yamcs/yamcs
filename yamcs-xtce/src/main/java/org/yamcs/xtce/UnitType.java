package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Used to hold the unit(s) plus possibly the exponent and factor for the units
 */
public class UnitType implements Serializable {
    private static final long serialVersionUID = -2505869748092316015L;

    String description;
    double power = 1;
    String factor = "1";
    private String unit;

    public UnitType(String unit) {
        this.unit = unit;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public double getPower() {
        return power;
    }
    
    public void setPower(double power) {
        this.power = power;
    }
    
    public String getFactor() {
        return factor;
    }
    
    public void setFactor(String factor) {
        this.factor = factor;
    }
    
    public String getUnit() {
        return unit;
    }

    
    @Override
    public String toString() {
        return "unit:"+unit+" desc: "+description+", power: "+power+", factor: "+factor;
    }
}
