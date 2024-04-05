import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Stream, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import * as utils from '../utils';

@Component({
  standalone: true,
  selector: 'app-stream-script-tab',
  templateUrl: './stream-script-tab.component.html',
  styleUrls: [
    './stream-script-tab.component.css',
    '../streamsql.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StreamScriptTabComponent {

  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const database = parent.parent!.paramMap.get('database')!;
    const name = parent.paramMap.get('stream')!;
    this.stream$ = yamcs.yamcsClient.getStream(database, name);
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
