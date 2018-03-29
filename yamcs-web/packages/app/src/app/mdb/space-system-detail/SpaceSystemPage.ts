import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Instance, SpaceSystem } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './SpaceSystemPage.html',
  styleUrls: ['./SpaceSystemPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemPage implements OnInit {

  qualifiedName: string;

  instance$: Observable<Instance>;
  spaceSystem$: Promise<SpaceSystem>;

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
    title: Title,
    private store: Store<State>,
  ) {
    this.qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.spaceSystem$ = yamcs.getSelectedInstance().getSpaceSystem(this.qualifiedName);
    title.setTitle(this.qualifiedName + ' - Yamcs');
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
