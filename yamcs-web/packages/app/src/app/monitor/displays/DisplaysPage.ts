import {
  Component,
  ChangeDetectionStrategy,
  ViewChild,
} from '@angular/core';

import { MatDialog } from '@angular/material';
import { SaveLayoutDialog } from './SaveLayoutDialog';
import { LayoutComponent } from './LayoutComponent';
import { LayoutState } from '@yamcs/displays';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Instance } from '@yamcs/client';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './DisplaysPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplaysPage {

  @ViewChild('layoutComponent')
  private layoutComponent: LayoutComponent;

  instance: Instance;
  initialLayout: LayoutState;

  // State as loaded freshly from sessionstorage
  initialState$ = new BehaviorSubject<LayoutState>({ frames: [] });

  // State as updated while the component is connected
  updatedState$ = new BehaviorSubject<LayoutState>({ frames: [] });

  constructor(private dialog: MatDialog, yamcs: YamcsService, title: Title) {
    title.setTitle('Displays - Yamcs');
    this.instance = yamcs.getInstance();

    // Attempt to restore state from session storage.
    // This way refresh or navigation don't just throw away all opened displays
    const item = sessionStorage.getItem(`yamcs.${this.instance.name}.layout`);
    if (item) {
      this.initialLayout = JSON.parse(item) as LayoutState;
      this.initialState$.next(this.initialLayout);
    } else {
      this.initialState$.next({ frames: [] });
    }

    // Ensure button state is correct on initial load with already open displays
    this.updatedState$.next(this.initialState$.getValue());
  }

  saveLayout() {
    this.dialog.open(SaveLayoutDialog, {
      width: '400px',
      data: { state: this.layoutComponent.getLayoutState() },
    });
  }

  onStateChange(instance: string, state: LayoutState) {
    this.updatedState$.next(state);
    sessionStorage.setItem(`yamcs.${instance}.layout`, JSON.stringify(state));
  }
}
