import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Instance, Stream } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './StreamPage.html',
  styleUrls: ['./StreamPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamPage {

  instance: Instance;
  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, title: Title) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name);
    this.instance = yamcs.getInstance();
    this.stream$ = yamcs.yamcsClient.getStream(this.instance.name, name);
  }
}
