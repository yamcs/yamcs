import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Stream } from '@yamcs/client';

import * as utils from '../utils';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

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
    this.stream$ = yamcs.getInstanceClient()!.getStream(name);
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
