import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { MessageService, PlaybackRequest, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { AgoPipe } from '../../shared/pipes/ago.pipe';
import { GapsPageTabsComponent } from '../gaps-page-tabs/gaps-page-tabs.component';

@Component({
  standalone: true,
  templateUrl: './gap-request-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AgoPipe,
    GapsPageTabsComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class GapRequestListComponent {

  displayedColumns = [
    'requestTime',
    'user',
    'vehicle',
    'packetType',
    'apid',
    'privateHeaderSource',
    'start',
    'stop',
    'status',
    'tmCount',
    'actions',
  ];

  interval$ = new BehaviorSubject<number | null>(null);
  period$ = new BehaviorSubject<number | null>(null);
  dataSource = new MatTableDataSource<PlaybackRequest>();

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    title: Title,
  ) {
    title.setTitle('Playback requests');
    this.loadData();
  }

  loadData() {
    this.yamcs.yamcsClient.getLinks(this.yamcs.instance!)
      .then(links => {
        let pbLink = null;
        for (const link of links) {
          if (link.type.indexOf('DassPlaybackPacketProvider') !== -1) {
            pbLink = link.name;
            break;
          }
        }
        if (pbLink) {
          this.yamcs.yamcsClient.getPlaybackInfo(this.yamcs.instance!, pbLink)
            .then(info => {
              if (info.interval) {
                this.interval$.next(utils.convertProtoDurationToMillis(info.interval));
              } else {
                this.interval$.next(null);
              }

              if (info.period) {
                this.period$.next(utils.convertProtoDurationToMillis(info.period));
              } else {
                this.period$.next(null);
              }

              this.dataSource.data = info.requests || [];
            }).catch(err => this.messageService.showError(err));
        }
      }).catch(err => this.messageService.showError(err));
  }
}
