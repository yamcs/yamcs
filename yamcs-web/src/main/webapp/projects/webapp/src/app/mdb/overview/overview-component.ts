import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';

import {
  MissionDatabase,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-mdb-overview',
  templateUrl: './overview-component.html',
  styleUrls: ['./overview-component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class OverviewComponent {
  mdb$: Promise<MissionDatabase>;

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
  ) {
    title.setTitle('Mission database');
    this.mdb$ = this.yamcs.yamcsClient.getMissionDatabase(
      this.yamcs.instance!,
    );
  }

  /* -------------------------------
   TrackBy helpers
  --------------------------------*/

  trackByName(_: number, item: any): string {
    return item.name;
  }

  trackByVersion(_: number, item: any): string {
    return item.version;
  }

  /* -------------------------------
   Message helpers
  --------------------------------*/

  hasUrl(message: string): boolean {
    return message.includes('http');
  }

  extractUrl(message: string): string | null {
    const match = message.match(/https?:\/\/\S+/);
    return match?.[0] ?? null;
  }

  extractText(message: string): string {
    return message.replace(/https?:\/\/\S+/, '').trim();
  }

  formatMessage(message: string): string {
    return message
      .replace(/\n/g, '<br>')
      .replace(/ {2,}/g, (spaces) => '&nbsp;'.repeat(spaces.length));
  }
}
