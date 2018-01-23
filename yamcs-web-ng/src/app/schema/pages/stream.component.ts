import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Stream } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import * as utils from '../utils';
import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './stream.component.html',
  styleUrls: ['./streamsql.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamPageComponent {

  stream$: Observable<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const name = route.snapshot.paramMap.get('name');
    if (name != null) {
      this.stream$ = yamcs.getSelectedInstance().getStream(name);
    }
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
