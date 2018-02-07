import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Event } from '../../../yamcs-client';

import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './events.component.html',
  // changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventsPageComponent {

  events$ = new BehaviorSubject<Event[]>([]);

  constructor(yamcs: YamcsService) {
    yamcs.getSelectedInstance().getEventUpdates().subscribe(evt => {
      this.processEvent(evt);
    });
  }

  private processEvent(evt: Event) {
    const events = this.events$.getValue().slice();
    events.push(evt);
    this.events$.next(events);
  }

  /*private sortAndEmitLinks() {
    const links = [];
    for (const name of Object.keys(this.linksByName)) {
      links.push(this.linksByName[name]);
    }
    this.links$.next(links.sort((a, b) => {
      if (a < b) {
        return -1;
      } else if (a > b) {
        return 1;
      } else {
        return 0;
      }
    }));
  }*/
}
