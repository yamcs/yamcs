package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.protobuf.PacketArchiveInfo;
import org.yamcs.protobuf.RebuildReceptionTimeIndexRequest;
import org.yamcs.security.SystemPrivilege;

public class PacketsReceptionPermissionsTest {
    @Test
    @SuppressWarnings("unchecked")
    void rebuildRequiresControlArchivingBeforeAccessingTheArchive() {
        Context context = mock(Context.class);
        doThrow(new ForbiddenException("Missing ControlArchiving")).when(context)
                .checkSystemPrivilege(SystemPrivilege.ControlArchiving);
        Observer<PacketArchiveInfo> observer = mock(Observer.class);
        assertThrows(ForbiddenException.class, () -> new PacketsApi().rebuildReceptionTimeIndex(context,
                RebuildReceptionTimeIndexRequest.newBuilder().setInstance("not-accessed").build(), observer));
        verify(context).checkSystemPrivilege(SystemPrivilege.ControlArchiving);
        verifyNoInteractions(observer);
    }
}
