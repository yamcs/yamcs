import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Stream } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './streams.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamsPageComponent {

  streams$: Observable<Stream[]>;

  constructor(yamcs: YamcsService) {
    this.streams$ = yamcs.getSelectedInstance().getStreams();
  }
}
