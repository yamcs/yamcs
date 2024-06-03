package org.yamcs.actions;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.ValidationException;
import org.yamcs.actions.Action.ActionStyle;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.ConfigApi;
import org.yamcs.protobuf.actions.ActionInfo;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;

public class ActionHelper {

    private static final Type HASHMAP_TYPE = new TypeToken<HashMap<String, Object>>() {
    }.getType();

    public static ActionInfo toActionInfo(Action<?> action) {
        return toActionInfo(action, true);
    }

    public static ActionInfo toActionInfo(Action<?> action, boolean addSpec) {
        var b = ActionInfo.newBuilder()
                .setId(action.getId())
                .setLabel(action.getLabel())
                .setStyle(action.getStyle().name())
                .setEnabled(action.isEnabled());
        if (action.getStyle() == ActionStyle.CHECK_BOX) {
            b.setChecked(action.isChecked());
        }
        var spec = action.getSpec();
        if (spec != null && !spec.getOptions().isEmpty()) {
            b.setSpec(ConfigApi.toSpecInfo(spec));
        }
        return b.build();
    }

    /**
     * Run an action on a target.
     */
    public static <T> void runAction(T target, Action<T> action, Struct options,
            Observer<Struct> responseObserver) throws HttpException {
        var gson = new Gson();
        JsonObject actionMessage = null;
        Map<String, Object> actionOptions = null;
        try {
            String json = JsonFormat.printer().print(options);
            actionMessage = gson.fromJson(json, JsonElement.class).getAsJsonObject();

            actionOptions = gson.fromJson(actionMessage, HASHMAP_TYPE);
        } catch (InvalidProtocolBufferException e) {
            // Should not happen, it's already been converted from JSON through transcoding
            throw new InternalServerErrorException(e);
        }

        if (!action.isEnabled()) {
            throw new BadRequestException("Action '" + action.getId() + "' is not enabled");
        }

        var spec = action.getSpec();
        if (spec != null) {
            try {
                // Validate, and apply defaults
                actionOptions = spec.validate(actionOptions);
                actionMessage = gson.toJsonTree(actionOptions, HASHMAP_TYPE).getAsJsonObject();
            } catch (ValidationException e) {
                throw new BadRequestException(e.getMessage());
            }
        }

        var actionResult = new ActionResult();
        action.execute(target, actionMessage, actionResult);
        actionResult.future().whenComplete((response, t) -> {
            if (t != null) {
                responseObserver.completeExceptionally(t);
            } else {
                if (response == null) {
                    responseObserver.next(Struct.getDefaultInstance());
                } else {
                    var json = response.toString();
                    var responseb = Struct.newBuilder();
                    try {
                        JsonFormat.parser().merge(json, responseb);
                    } catch (InvalidProtocolBufferException e) {
                        throw new InternalServerErrorException(e);
                    }
                    responseObserver.next(responseb.build());
                }
            }
        });
    }
}
