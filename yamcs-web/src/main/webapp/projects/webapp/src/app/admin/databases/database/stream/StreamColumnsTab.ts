import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Stream } from '@yamcs/webapp-sdk';
import { YamcsService } from '../../../../core/services/YamcsService';

@Component({
  templateUrl: './StreamColumnsTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamColumnsTab {

  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const database = parent.parent!.paramMap.get('database')!;
    const name = parent.paramMap.get('stream')!;
    this.stream$ = yamcs.yamcsClient.getStream(database, name);
  }
}
