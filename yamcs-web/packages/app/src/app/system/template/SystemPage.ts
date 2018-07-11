import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Instance } from '@yamcs/client';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';
import { User } from '../../shared/User';

@Component({
  templateUrl: './SystemPage.html',
  styleUrls: ['./SystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemPage {

  instance: Instance;
  user: User;

  constructor(yamcs: YamcsService, authService: AuthService) {
    this.instance = yamcs.getInstance();
    this.user = authService.getUser()!;
  }

  showServicesItem() {
    return this.user.hasSystemPrivilege('ControlServices');
  }

  showTablesItem() {
    return this.user.hasSystemPrivilege('ReadTables');
  }

  showStreamsItem() {
    const objectPrivileges = this.user.getObjectPrivileges();
    for (const priv of objectPrivileges) {
      if (priv.type === 'Stream') {
        return true;
      }
    }
    return this.user.isSuperuser();
  }
}
