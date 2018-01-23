import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { DisplayInfo } from '../../../yamcs-client';
import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './displays.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplaysPageComponent {

  displayInfo$: Observable<DisplayInfo>;

  constructor(yamcs: YamcsService) {
    this.displayInfo$ = yamcs.getSelectedInstance().getDisplayInfo();
  }
}
