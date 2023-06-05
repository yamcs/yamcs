import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { MissionDatabase } from '@yamcs/webapp-sdk';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './OverviewPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewPage {

  mdb$: Promise<MissionDatabase>;

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('Mission database');
    this.mdb$ = yamcs.yamcsClient.getMissionDatabase(yamcs.instance!);
  }
}
