import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { GeneralInfo } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';



@Component({
  templateUrl: './ServerInfoPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServerInfoPage {

  info$: Promise<GeneralInfo>;

  constructor(private yamcs: YamcsService, title: Title) {
    title.setTitle('Server Info - Yamcs');
    this.info$ = yamcs.yamcsClient.getGeneralInfo();
  }
}
