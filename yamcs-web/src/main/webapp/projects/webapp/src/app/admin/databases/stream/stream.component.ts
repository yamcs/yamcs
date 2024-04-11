import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Stream, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-stream-page',
  templateUrl: './stream.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class StreamComponent {

  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, title: Title) {
    const snapshot = route.snapshot;
    const database = snapshot.parent!.paramMap.get('database')!;
    const name = snapshot.paramMap.get('stream')!;
    title.setTitle(name);
    this.stream$ = yamcs.yamcsClient.getStream(database, name);
  }
}
