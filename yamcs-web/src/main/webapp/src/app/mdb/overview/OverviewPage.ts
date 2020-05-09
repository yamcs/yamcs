import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { MissionDatabase } from '../../client';
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
    title.setTitle('Mission Database');
    this.mdb$ = yamcs.yamcsClient.getMissionDatabase(yamcs.instance!);
  }
}
