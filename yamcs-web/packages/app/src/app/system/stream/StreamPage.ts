import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Stream, Instance } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

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
    title.setTitle(name + ' - Yamcs');
    this.stream$ = yamcs.getInstanceClient()!.getStream(name);
    this.instance = yamcs.getInstance();
  }
}
