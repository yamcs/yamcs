import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Stream } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../utils';

@Component({
  templateUrl: './StreamScriptTab.html',
  styleUrls: [
    './StreamScriptTab.css',
    '../streamsql.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamScriptTab {

  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;
    this.stream$ = yamcs.yamcsClient.getStream(yamcs.getInstance().name, name);
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
