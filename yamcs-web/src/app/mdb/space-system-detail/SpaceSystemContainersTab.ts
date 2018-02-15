import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Instance, Container } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './SpacesystemContainersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemContainersTab implements OnInit {

  instance$: Observable<Instance>;
  containers$: Observable<Container[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>) {
    const parent = route.snapshot.parent;
    if (parent) {
      const qualifiedName = parent.paramMap.get('qualifiedName');
      if (qualifiedName != null) {
        this.containers$ = yamcs.getSelectedInstance().getContainers({
          namespace: qualifiedName
        });
      }
    }
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
