import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SystemPage.html',
  styleUrls: ['./SystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemPage {

  instance: Instance;

  constructor(yamcs: YamcsService) {
    this.instance = yamcs.getInstance();
  }
}
