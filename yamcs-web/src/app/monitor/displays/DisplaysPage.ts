import {
  Component,
  ChangeDetectionStrategy,
  ViewChild,
} from '@angular/core';

import { MatDialog } from '@angular/material';
import { SaveLayoutDialog } from './SaveLayoutDialog';
import { LayoutComponent } from './LayoutComponent';
import { LayoutState } from './LayoutState';
import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

@Component({
  templateUrl: './DisplaysPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplaysPage {

  @ViewChild('layoutComponent')
  private layoutComponent: LayoutComponent;

  initialLayout: LayoutState;
  state$: BehaviorSubject<LayoutState>;

  constructor(private yamcs: YamcsService, private dialog: MatDialog) {
    // Attempt to restore state from session storage.
    // This way refresh or navigation don't just throw away all opened displays
    const instance = this.yamcs.getSelectedInstance().instance;
    const item = sessionStorage.getItem(`yamcs.${instance}.layout`);
    if (item) {
      this.initialLayout = JSON.parse(item) as LayoutState;
      this.state$ = new BehaviorSubject<LayoutState>(this.initialLayout);
    } else {
      this.state$ = new BehaviorSubject<LayoutState>({ frames: [] });
    }
  }

  saveLayout() {
    this.dialog.open(SaveLayoutDialog, {
      width: '400px',
      data: { state: this.layoutComponent.getLayoutState() },
    });
  }

  onStateChange(state: LayoutState) {
    this.state$.next(state);
    const instance = this.yamcs.getSelectedInstance().instance;
    sessionStorage.setItem(`yamcs.${instance}.layout`, JSON.stringify(state));
  }
}
