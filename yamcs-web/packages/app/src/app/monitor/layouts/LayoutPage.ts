import { Component, ChangeDetectionStrategy, ViewChild } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { Instance } from '@yamcs/client';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { ActivatedRoute, Router } from '@angular/router';
import { LayoutState } from '@yamcs/displays';
import { LayoutComponent } from '../displays/LayoutComponent';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { NamedLayout, LayoutStorage } from '../displays/LayoutStorage';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './LayoutPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutPage {

  @ViewChild('layoutComponent')
  private layoutComponent: LayoutComponent;

  instance$: Observable<Instance>;
  layout: NamedLayout;

  dirty$ = new BehaviorSubject<boolean>(false);

  constructor(route: ActivatedRoute, store: Store<State>, private router: Router, title: Title) {
    this.instance$ = store.select(selectCurrentInstance);
    const layoutName = route.snapshot.paramMap.get('name')!;
    title.setTitle(layoutName + ' - Yamcs');

    this.instance$.subscribe(instance => {
      this.layout = LayoutStorage.getLayout(instance.name, layoutName);
    });
  }

  saveLayout() {
    this.instance$.subscribe(instance => {
      const state = this.layoutComponent.getLayoutState();
      LayoutStorage.saveLayout(instance.name, this.layout.name, state);
      this.dirty$.next(false);
    });
  }

  renameLayout() {
    // TODO
  }

  removeLayout() {
    if (confirm('Do you want to permanently delete this layout?')) {
      this.instance$.subscribe(instance => {
        LayoutStorage.deleteLayout(instance.name, this.layout.name);
        this.router.navigateByUrl(`/monitor/layouts?instance=${instance.name}`);
      });
    }
  }

  onStateChange(state: LayoutState) {
    this.dirty$.next(true);
  }
}
