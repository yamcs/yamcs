package org.yamcs.http.audit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.yamcs.InitException;
import org.yamcs.YamcsServer;
import org.yamcs.http.AbstractHttpService;
import org.yamcs.http.Context;
import org.yamcs.http.HttpServer;
import org.yamcs.security.User;
import org.yamcs.yarch.YarchDatabase;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

public class AuditLog extends AbstractHttpService {

    private ConcurrentMap<String, AuditLogDb> dbs = new ConcurrentHashMap<>();
    private Map<String, AuditLogPrivilegeChecker> privilegeCheckers = new HashMap<>();

    @Override
    public void init(HttpServer httpServer) throws InitException {
        // Prepopulate at least the _global instance, for catching early errors
        dbs.put(YamcsServer.GLOBAL_INSTANCE, new AuditLogDb(YamcsServer.GLOBAL_INSTANCE));
    }

    public void addPrivilegeChecker(String service, AuditLogPrivilegeChecker checker) {
        privilegeCheckers.put(service, checker);
    }

    public boolean validateAccess(String service, User user) {
        AuditLogPrivilegeChecker checker = privilegeCheckers.get(service);
        return checker != null ? checker.validate(user) : false;
    }

    public List<String> getServices(User user) {
        return privilegeCheckers.entrySet().stream()
                .filter(entry -> entry.getValue().validate(user))
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());
    }

    public void addRecord(Context ctx, Message request, String summary) {
        String yamcsInstance = YamcsServer.GLOBAL_INSTANCE;
        for (FieldDescriptor descriptor : request.getDescriptorForType().getFields()) {
            if ("instance".equals(descriptor.getName())) {
                yamcsInstance = (String) request.getField(descriptor);
            }
        }
        getAuditLogDb(yamcsInstance).addRecord(ctx.getMethod(), request, ctx.user, summary);
    }

    public void listRecords(String yamcsInstance, int limit, String token, AuditRecordFilter filter,
            AuditRecordListener consumer) {
        getAuditLogDb(yamcsInstance).listRecords(limit, token, filter, consumer);
    }

    private AuditLogDb getAuditLogDb(String yamcsInstance) {
        // Early stop to prevent YarchDatabase.getInstance from creating an instance
        if (!YarchDatabase.hasInstance(yamcsInstance)) {
            throw new IllegalArgumentException("Unknown instance " + yamcsInstance);
        }
        return dbs.computeIfAbsent(yamcsInstance, yi -> {
            try {
                return new AuditLogDb(yamcsInstance);
            } catch (InitException e) {
                throw new Error(e);
            }
        });
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }
}
