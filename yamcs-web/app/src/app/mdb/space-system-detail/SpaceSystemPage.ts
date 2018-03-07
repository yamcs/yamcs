import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Instance, SpaceSystem } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { LocationStrategy } from '@angular/common';

@Component({
  templateUrl: './SpaceSystemPage.html',
  styleUrls: ['./SpaceSystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemPage implements OnInit {

  qualifiedName: string;

  instance$: Observable<Instance>;
  spaceSystem$: Observable<SpaceSystem>;

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
    private store: Store<State>,
    private url: LocationStrategy,
  ) {
    this.qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.spaceSystem$ = yamcs.getSelectedInstance().getSpaceSystem(this.qualifiedName);
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }

  isDetailPageOf(segment: string) {
    const currentUrl = this.url.path().split('?')[0];
    return currentUrl.indexOf(segment) !== -1 && !currentUrl.endsWith(segment);
  }
}
