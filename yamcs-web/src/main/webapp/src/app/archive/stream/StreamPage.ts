import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Stream } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './StreamPage.html',
  styleUrls: ['./StreamPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamPage {

  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, title: Title) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name);
    this.stream$ = yamcs.yamcsClient.getStream(yamcs.instance!, name);
  }
}
