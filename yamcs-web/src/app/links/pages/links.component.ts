import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Link, LinkEvent } from '../../../yamcs-client';

import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './links.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinksPageComponent {

  links$ = new BehaviorSubject<Link[]>([]);

  private linksByName: { [key: string]: Link } = {};

  constructor(yamcs: YamcsService) {
    yamcs.getSelectedInstance().getLinkUpdates().subscribe(evt => {
      this.processLinkEvent(evt);
    });
  }

  private processLinkEvent(evt: LinkEvent) {
    switch (evt.type) {
      case 'REGISTERED':
      case 'UPDATED':
        this.linksByName[evt.linkInfo.name] = evt.linkInfo;
        this.sortAndEmitLinks();
        break;
      case 'UNREGISTERED':
        delete this.linksByName[evt.linkInfo.name];
        this.sortAndEmitLinks();
        break;
      default:
        console.error('Unexpected link update of type ' + evt.type);
        break;
    }
  }

  private sortAndEmitLinks() {
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
  }
}
