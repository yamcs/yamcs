import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Command } from '@yamcs/client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { YamcsService } from '../../core/services/YamcsService';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './CommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPage {

  instance$: Observable<Instance>;
  command$: Promise<Command>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, store: Store<State>, title: Title) {
    this.instance$ = store.select(selectCurrentInstance);

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.command$ = yamcs.getSelectedInstance().getCommand(qualifiedName);
    this.command$.then(command => {
      title.setTitle(command.name + ' - Yamcs');
    });
  }
}
