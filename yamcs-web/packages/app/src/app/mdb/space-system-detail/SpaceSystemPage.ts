import { Component, ChangeDetectionStrategy } from '@angular/core';

import { Instance, SpaceSystem } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './SpaceSystemPage.html',
  styleUrls: ['./SpaceSystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemPage {

  qualifiedName: string;

  instance: Instance;
  spaceSystem$: Promise<SpaceSystem>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, title: Title) {
    this.qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.spaceSystem$ = yamcs.getInstanceClient()!.getSpaceSystem(this.qualifiedName);
    title.setTitle(this.qualifiedName + ' - Yamcs');
    this.instance = yamcs.getInstance();
  }
}
