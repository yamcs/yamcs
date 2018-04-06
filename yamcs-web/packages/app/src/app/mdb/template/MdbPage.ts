import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './MdbPage.html',
  styleUrls: ['./MdbPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MdbPage {

  instance: Instance;

  constructor(yamcs: YamcsService) {
    this.instance = yamcs.getInstance();
  }
}
