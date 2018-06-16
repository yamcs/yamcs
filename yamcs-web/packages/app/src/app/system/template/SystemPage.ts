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
    return this.authService.hasSystemPrivilege('ControlServices');
  }

  showTablesItem() {
    return this.authService.hasSystemPrivilege('ReadTables');
  }

  showStreamsItem() {
    const userInfo = this.authService.getUserInfo()!;
    return userInfo.systemPrivilege && userInfo.systemPrivilege.length > 0;
  }
}
