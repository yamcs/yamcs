import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './MonitorPage.html',
  styleUrls: ['./MonitorPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorPage {

  instance: Instance;

  constructor(yamcs: YamcsService) {
    this.instance = yamcs.getInstance();
  }
}
