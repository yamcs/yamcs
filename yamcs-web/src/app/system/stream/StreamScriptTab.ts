import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Stream } from '../../../yamcs-client';

import * as utils from '../utils';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './StreamScriptTab.html',
  styleUrls: ['../streamsql.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamScriptTab {

  stream$: Observable<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const parent = route.snapshot.parent;
    if (parent) {
      const name = parent.paramMap.get('name');
      if (name != null) {
        this.stream$ = yamcs.getSelectedInstance().getStream(name);
      }
    }
  }


  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
