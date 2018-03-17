import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Container } from '@yamcs/client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './ContainerPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainerPage {

  instance$: Observable<Instance>;
  container$: Promise<Container>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, store: Store<State>, title: Title) {
    this.instance$ = store.select(selectCurrentInstance);

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.container$ = yamcs.getSelectedInstance().getContainer(qualifiedName);
    this.container$.then(container => {
      title.setTitle(container.name + ' - Yamcs');
    });
  }
}
