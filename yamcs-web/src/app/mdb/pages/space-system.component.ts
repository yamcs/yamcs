import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Instance, SpaceSystem } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/yamcs.service';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './space-system.component.html',
  styleUrls: ['./space-system.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemPageComponent implements OnInit {

  instance$: Observable<Instance>;
  spaceSystem$: Observable<SpaceSystem>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>) {
    const qualifiedName = route.snapshot.paramMap.get('qualifiedName');
    if (qualifiedName != null) {
      this.spaceSystem$ = yamcs.getSelectedInstance().getSpaceSystem(qualifiedName);
    }
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
