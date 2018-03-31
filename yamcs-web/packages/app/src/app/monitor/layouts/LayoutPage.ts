import { Component, ChangeDetectionStrategy, ViewChild } from '@angular/core';
import { Instance } from '@yamcs/client';
import { ActivatedRoute, Router } from '@angular/router';
import { LayoutState } from '@yamcs/displays';
import { LayoutComponent } from '../displays/LayoutComponent';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { NamedLayout, LayoutStorage } from '../displays/LayoutStorage';
import { Title } from '@angular/platform-browser';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './LayoutPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutPage {

  @ViewChild('layoutComponent')
  private layoutComponent: LayoutComponent;

  instance: Instance;
  layout: NamedLayout;

  dirty$ = new BehaviorSubject<boolean>(false);

  constructor(route: ActivatedRoute, yamcs: YamcsService, private router: Router, title: Title) {
    this.instance = yamcs.getInstance();
    const layoutName = route.snapshot.paramMap.get('name')!;
    title.setTitle(layoutName + ' - Yamcs');

    this.layout = LayoutStorage.getLayout(this.instance.name, layoutName);
  }

  saveLayout() {
    const state = this.layoutComponent.getLayoutState();
    LayoutStorage.saveLayout(this.instance.name, this.layout.name, state);
    this.dirty$.next(false);
  }

  renameLayout() {
    // TODO
  }

  removeLayout() {
    if (confirm('Do you want to permanently delete this layout?')) {
      LayoutStorage.deleteLayout(this.instance.name, this.layout.name);
      this.router.navigateByUrl(`/monitor/layouts?instance=${this.instance.name}`);
    }
  }

  onStateChange(state: LayoutState) {
    this.dirty$.next(true);
  }
}
