import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Instance } from '@yamcs/client';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SystemPage.html',
  styleUrls: ['./SystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemPage {

  instance: Instance;

  constructor(yamcs: YamcsService, private authService: AuthService) {
    this.instance = yamcs.getInstance();
  }

  showServicesItem() {
    return this.authService.hasSystemPrivilege('MayControlServices');
  }

  showTablesItem() {
    return this.authService.hasSystemPrivilege('MayReadTables');
  }

  showStreamsItem() {
    const userInfo = this.authService.getUserInfo();
    if (userInfo && userInfo.streamPrivileges) {
      return userInfo.streamPrivileges.length > 0;
    }
    return false;
  }
}
