package org.yamcs.http.audit;

import org.yamcs.security.User;

@FunctionalInterface
public interface AuditLogPrivilegeChecker {

    boolean validate(User user);
}
