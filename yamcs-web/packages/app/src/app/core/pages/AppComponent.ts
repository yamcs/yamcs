import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Instance, UserInfo } from '@yamcs/client';
import { YamcsService } from '../services/YamcsService';
import { MatDialog } from '@angular/material';
import { SelectInstanceDialog } from '../../shared/template/SelectInstanceDialog';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'app-root',
  templateUrl: './AppComponent.html',
  styleUrls: ['./AppComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {

  title = 'Yamcs';

  instance$: Observable<Instance | null>;
  user$: Promise<UserInfo>;

  constructor(yamcs: YamcsService, private dialog: MatDialog) {
    this.instance$ = yamcs.instance$;
    this.user$ = yamcs.yamcsClient.getUserInfo();
  }

  openInstanceDialog() {
    this.dialog.open(SelectInstanceDialog, {
      width: '600px',
      autoFocus: false,
    });
  }
}
