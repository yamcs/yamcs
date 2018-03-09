import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Container } from '@yamcs/client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SpaceSystemContainerTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemContainerTab {

  instance$: Observable<Instance>;
  container$: Promise<Container>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.container$ = yamcs.getSelectedInstance().getContainer(qualifiedName);
  }
}
