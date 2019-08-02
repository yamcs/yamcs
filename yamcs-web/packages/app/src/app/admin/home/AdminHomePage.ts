import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { GeneralInfo } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './AdminHomePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminHomePage {

  info$: Promise<GeneralInfo>;

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Admin Area');
    this.info$ = yamcs.yamcsClient.getGeneralInfo();
  }
}
