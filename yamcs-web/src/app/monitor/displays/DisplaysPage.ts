import {
  Component,
  ChangeDetectionStrategy,
  ViewChild,
} from '@angular/core';

import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { MatDialog } from '@angular/material';
import { SaveLayoutDialog } from './SaveLayoutDialog';
import { LayoutComponent } from './LayoutComponent';
import { LayoutState } from './LayoutState';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './DisplaysPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplaysPage {

  @ViewChild('layout')
  private layoutComponent: LayoutComponent;

  navigatorOpen$ = new BehaviorSubject<boolean>(true);

  constructor(private yamcs: YamcsService, private dialog: MatDialog) {
  }

  toggleNavigator() {
    this.navigatorOpen$.next(!this.navigatorOpen$.getValue());
  }

  saveLayout() {
    this.dialog.open(SaveLayoutDialog, {
      width: '250px',
      data: { state: this.layoutComponent.getLayoutState() },
    });
  }

  onStateChange(state: LayoutState) {
    const instance = this.yamcs.getSelectedInstance().instance;
    sessionStorage.setItem(`yamcs.${instance}.layout`, JSON.stringify(state));
  }

  tileDisplays() {
    this.layoutComponent.tileFrames();
  }

  cascadeDisplays() {
    this.layoutComponent.cascadeFrames();
  }
}
