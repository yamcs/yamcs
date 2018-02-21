import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { Instance, UserInfo } from '../../../yamcs-client';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../store/instance.selectors';
import { YamcsService } from '../services/YamcsService';
import { MatDialog } from '@angular/material';
import { SelectInstanceDialog } from '../../shared/template/SelectInstanceDialog';

@Component({
  selector: 'app-root',
  templateUrl: './AppComponent.html',
  styleUrls: ['./AppComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {

  title = 'Yamcs';

  instance$: Observable<Instance>;
  user$: Observable<UserInfo>;

  constructor(yamcs: YamcsService, store: Store<State>, private dialog: MatDialog) {
    this.instance$ = store.select(selectCurrentInstance);
    this.user$ = yamcs.yamcsClient.getUserInfo();
  }

  openInstanceDialog() {
    this.dialog.open(SelectInstanceDialog, {
      width: '600px',
      autoFocus: false,
    });
  }
}
