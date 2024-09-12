import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { MissionDatabase, MissionDatabaseVersion, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './overview-component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class OverviewComponent {

  mdb$: Promise<MissionDatabase>;
  mdbV$: Promise<MissionDatabaseVersion>

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('Mission database');
    this.mdb$ = yamcs.yamcsClient.getMissionDatabase(yamcs.instance!);
    this.mdbV$ = yamcs.yamcsClient.getMissionDatabaseVersion(yamcs.instance!);
  }
}
