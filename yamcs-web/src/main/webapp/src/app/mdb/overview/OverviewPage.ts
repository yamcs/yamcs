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

  instance: string;

  constructor(
    yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('Mission Database');
    this.instance = yamcs.getInstance();
    this.mdb$ = yamcs.yamcsClient.getMissionDatabase(this.instance);
  }
}
