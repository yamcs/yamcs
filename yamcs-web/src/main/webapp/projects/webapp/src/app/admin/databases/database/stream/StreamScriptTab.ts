import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Stream, YamcsService } from '@yamcs/webapp-sdk';
import * as utils from '../../utils';

@Component({
  templateUrl: './StreamScriptTab.html',
  styleUrls: [
    './StreamScriptTab.css',
    '../../streamsql.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamScriptTab {

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
