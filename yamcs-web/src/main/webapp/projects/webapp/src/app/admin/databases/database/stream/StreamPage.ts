import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Stream } from '@yamcs/webapp-sdk';
import { YamcsService } from '../../../../core/services/YamcsService';

@Component({
  templateUrl: './StreamPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamPage {

  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, title: Title) {
    const snapshot = route.snapshot;
    const database = snapshot.parent!.paramMap.get('database')!;
    const name = snapshot.paramMap.get('stream')!;
    title.setTitle(name);
    this.stream$ = yamcs.yamcsClient.getStream(database, name);
  }
}
