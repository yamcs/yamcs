package org.yamcs.xtce;

import java.io.Serializable;

/**
 * XTCE: This type describes how a numeric value should be represented in engineering/calibrated form. The defaults
 * reflect the most common form.
 */
public class NumberFormatType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to the
     * radix. Default is base 10.
     */
    private RadixType numberBase = RadixType.DECIMAL;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to the
     * minimum number of fractional digits. The default is 0.
     */
    private int minimumFractionDigits = 0;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to the
     * maximum or upper bound of the number of digits. There is no default. No value specified should be interpreted as
     * no upper bound such that all requires digits are used to fully characterize the value.
     */
    private int maximumFractionDigits = -1;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to the
     * minimum number of integer digits. The default is 1.
     */
    private int minimumIntegerDigits = 1;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to the
     * maximum or upper bound of the integer digits. There is no default. No value specified should be interpreted as no
     * upper bound such that all requires digits are used to fully characterize the value.
     */
    private int maximumIntegerDigits = -1;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to negative
     * values. This attribute specifies the character or characters that should be appended to the numeric value to
     * indicate negative values. The default is none.
     */
    private String negativeSuffix;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to positive
     * values. This attribute specifies the character or characters that should be appended to the numeric value to
     * indicate positive values. The default is none. Zero is considered to be specific to the implementation/platform
     * and is not implied here.
     */
    private String positiveSuffix;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to negative
     * values. This attribute specifies the character or characters that should be prepended to the numeric value to
     * indicate negative values. The default is a minus character "-".
     */
    private String negativePrefix = "-";

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to positive
     * values. This attribute specifies the character or characters that should be prepended to the numeric value to
     * indicate positive values. The default is none. Zero is considered to be specific to the implementation/platform
     * and is not implied here.
     */
    private String positivePrefix;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to larger
     * values. Groupings by thousand are specific to locale, so the schema only specifies whether they will be present
     * and not which character separators are used. The default is false.
     */
    private boolean showThousandsGrouping = false;

    /**
     * XTCE: Describes how the engineering/calibrated value of this number should be displayed with respect to notation.
     * Engineering, scientific, or traditional decimal notation may be specified. The precise characters used is locale
     * specific for the implementation/platform. The default is "normal" for the traditional notation.
     */
    private FloatingPointNotationType notation = FloatingPointNotationType.NORMAL;

    public RadixType getNumberBase() {
        return numberBase;
    }

    public void setNumberBase(RadixType numberBase) {
        this.numberBase = numberBase;
    }

    public int getMinimumFractionDigits() {
        return minimumFractionDigits;
    }

    public void setMinimumFractionDigits(int minimumFractionDigits) {
        this.minimumFractionDigits = minimumFractionDigits;
    }

    public int getMaximumFractionDigits() {
        return maximumFractionDigits;
    }

    public void setMaximumFractionDigits(int maximumFractionDigits) {
        this.maximumFractionDigits = maximumFractionDigits;
    }

    public int getMinimumIntegerDigits() {
        return minimumIntegerDigits;
    }

    public void setMinimumIntegerDigits(int minimumIntegerDigits) {
        this.minimumIntegerDigits = minimumIntegerDigits;
    }

    public int getMaximumIntegerDigits() {
        return maximumIntegerDigits;
    }

    public void setMaximumIntegerDigits(int maximumIntegerDigits) {
        this.maximumIntegerDigits = maximumIntegerDigits;
    }

    public String getNegativeSuffix() {
        return negativeSuffix;
    }

    public void setNegativeSuffix(String negativeSuffix) {
        this.negativeSuffix = negativeSuffix;
    }

    public String getPositiveSuffix() {
        return positiveSuffix;
    }

    public void setPositiveSuffix(String positiveSuffix) {
        this.positiveSuffix = positiveSuffix;
    }

    public String getNegativePrefix() {
        return negativePrefix;
    }

    public void setNegativePrefix(String negativePrefix) {
        this.negativePrefix = negativePrefix;
    }

    public String getPositivePrefix() {
        return positivePrefix;
    }

    public void setPositivePrefix(String positivePrefix) {
        this.positivePrefix = positivePrefix;
    }

    public boolean isShowThousandsGrouping() {
        return showThousandsGrouping;
    }

    public void setShowThousandsGrouping(boolean showThousandsGrouping) {
        this.showThousandsGrouping = showThousandsGrouping;
    }

    public FloatingPointNotationType getNotation() {
        return notation;
    }

    public void setNotation(FloatingPointNotationType notation) {
        this.notation = notation;
    }
}
