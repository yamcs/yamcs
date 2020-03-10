import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Stream } from '../../client';
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
        this.stream$ = yamcs.yamcsClient.getStream(yamcs.getInstance(), name);
      }
    }
  }
}
