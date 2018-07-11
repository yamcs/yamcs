import { ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import * as screenfull from 'screenfull';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';
import { Layout } from './Layout';
import { LayoutState } from './LayoutState';

@Component({
  templateUrl: './LayoutPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutPage implements OnDestroy {

  @ViewChild('layout')
  private layout: Layout;

  instance: Instance;

  layoutName: string;
  layoutState$: Promise<LayoutState>;

  dirty$ = new BehaviorSubject<boolean>(false);

  fullscreen$ = new BehaviorSubject<boolean>(false);
  fullscreenListener: () => void;

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private router: Router,
    title: Title,
    private authService: AuthService,
  ) {
    this.instance = yamcs.getInstance();
    this.layoutName = route.snapshot.paramMap.get('name')!;
    title.setTitle(this.layoutName + ' - Yamcs');

    this.fullscreenListener = () => this.fullscreen$.next(screenfull.isFullscreen);
    screenfull.on('change', this.fullscreenListener);

    this.layoutState$ = new Promise<LayoutState>((resolve, reject) => {
      const username = authService.getUser()!.getUsername();
      const objectName = 'layouts/' + this.layoutName;
      yamcs.getInstanceClient()!.getObject(`user.${username}` /* FIXME */, objectName).then(response => {
        response.json().then(layoutState => resolve(layoutState)).catch(err => reject(err));
      }).catch(err => reject(err));
    });
  }

  saveLayout() {
    const username = this.authService.getUser()!.getUsername();
    const state = this.layout.getLayoutState();
    const objectName = `layouts/${this.layoutName}`;
    const objectValue = new Blob([JSON.stringify(state, undefined, 2)], {
      type: 'application/json',
    });
    this.yamcs.getInstanceClient()!.uploadObject(`user.${username}`, objectName, objectValue).then(() => {
      this.dirty$.next(false);
    });
  }

  removeLayout() {
    if (confirm('Do you want to permanently delete this layout?')) {
      const username = this.authService.getUser()!.getUsername();
      const objectName = `layouts/${this.layoutName}`;
      this.yamcs.getInstanceClient()!.deleteObject(`user.${username}`, objectName).then(() => {
        this.router.navigateByUrl(`/monitor/layouts?instance=${this.instance.name}`);
      });
    }
  }

  onStateChange(state: LayoutState) {
    this.dirty$.next(true);
  }

  goFullscreen() {
    if (screenfull.enabled) {
      screenfull.request(this.layout.wrapperRef.nativeElement);
    } else {
      alert('Your browser does not appear to support going full screen');
    }
  }

  ngOnDestroy() {
    screenfull.off('change', this.fullscreenListener);
  }
}
