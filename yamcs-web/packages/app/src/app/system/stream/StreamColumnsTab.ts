import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Stream } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './StreamColumnsTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamColumnsTab {

  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent;
    if (parent) {
      const name = parent.paramMap.get('name');
      if (name != null) {
        this.stream$ = yamcs.getInstanceClient()!.getStream(name);
      }
    }
  }
}
