package org.yamcs.http.api;

import org.yamcs.Spec;
import org.yamcs.Spec.Option;
import org.yamcs.Spec.WhenCondition;
import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.protobuf.config.OptionGroupInfo;
import org.yamcs.protobuf.config.OptionInfo;
import org.yamcs.protobuf.config.OptionType;
import org.yamcs.protobuf.config.SpecInfo;
import org.yamcs.protobuf.config.WhenConditionInfo;

public class ConfigApi {

    public static SpecInfo toSpecInfo(Spec spec) {
        var specb = SpecInfo.newBuilder()
                .setAllowUnknownKeys(spec.isAllowUnknownKeys());
        for (var option : spec.getOptions()) {
            specb.addOptions(toOptionInfo(option, spec));
        }
        for (var group : spec.getRequiredOneOfGroups()) {
            specb.addRequiredOneOf(OptionGroupInfo.newBuilder()
                    .addAllKeys(group));
        }
        for (var group : spec.getRequireTogetherGroups()) {
            specb.addRequireTogether(OptionGroupInfo.newBuilder()
                    .addAllKeys(group));
        }
        for (var condition : spec.getWhenConditions()) {
            specb.addWhenConditions(toWhenConditionInfo(condition));
        }
        return specb.build();
    }

    private static WhenConditionInfo toWhenConditionInfo(WhenCondition condition) {
        var conditionb = WhenConditionInfo.newBuilder()
                .setKey(condition.getKey())
                .setValue(WellKnownTypes.toValue(condition.getValue()))
                .addAllRequiredKeys(condition.getRequiredKeys());
        return conditionb.build();
    }

    private static OptionInfo toOptionInfo(Option option, Spec spec) {
        var optionb = OptionInfo.newBuilder()
                .setName(option.getName())
                .setType(OptionType.valueOf(option.getType().name()))
                .setRequired(option.isRequired())
                .setHidden(option.isHidden())
                .setSecret(option.isSecret());

        if (option.getTitle() != null) {
            optionb.setTitle(option.getTitle());
        }
        if (option.getDescription() != null) {
            optionb.addAllDescription(option.getDescription());
        }
        if (option.getDefaultValue() != null) {
            var defaultValue = WellKnownTypes.toValue(option.getDefaultValue());
            optionb.setDefault(defaultValue);
        }
        if (option.getElementType() != null) {
            optionb.setElementType(OptionType.valueOf(option.getElementType().name()));
        }
        if (option.getVersionAdded() != null) {
            optionb.setVersionAdded(option.getVersionAdded());
        }
        if (option.getDeprecationMessage() != null) {
            optionb.setDeprecationMessage(option.getDeprecationMessage());
        }
        if (option.getChoices() != null) {
            for (var choice : option.getChoices()) {
                optionb.addChoices(WellKnownTypes.toValue(choice));
            }
        }
        if (option.getSpec() != null) {
            optionb.setSpec(toSpecInfo(option.getSpec()));
            optionb.setApplySpecDefaults(option.isApplySpecDefaults());
        }
        optionb.addAllAliases(spec.getAliases(option));

        return optionb.build();
    }
}
