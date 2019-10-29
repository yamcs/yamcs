import { ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance, StorageClient } from '@yamcs/client';
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

  @ViewChild('layout', { static: false })
  private layout: Layout;

  instance: Instance;

  layoutName: string;
  layoutState$: Promise<LayoutState>;

  dirty$ = new BehaviorSubject<boolean>(false);

  fullscreen$ = new BehaviorSubject<boolean>(false);
  fullscreenListener: () => void;

  private storageClient: StorageClient;

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
    private router: Router,
    title: Title,
    private authService: AuthService,
  ) {
    this.instance = yamcs.getInstance();
    this.storageClient = yamcs.createStorageClient();

    this.layoutName = route.snapshot.paramMap.get('name')!;
    title.setTitle(this.layoutName);

    this.fullscreenListener = () => this.fullscreen$.next(screenfull.isFullscreen);
    screenfull.on('change', this.fullscreenListener);

    this.layoutState$ = new Promise<LayoutState>((resolve, reject) => {
      const username = authService.getUser()!.getName();
      const objectName = 'layouts/' + this.layoutName;
      this.storageClient.getObject('_global', `user.${username}` /* FIXME */, objectName).then(response => {
        response.json().then(layoutState => resolve(layoutState)).catch(err => reject(err));
      }).catch(err => reject(err));
    });
  }

  saveLayout() {
    const username = this.authService.getUser()!.getName();
    const state = this.layout.getLayoutState();
    const objectName = `layouts/${this.layoutName}`;
    const objectValue = new Blob([JSON.stringify(state, undefined, 2)], {
      type: 'application/json',
    });
    this.storageClient.uploadObject('_global', `user.${username}`, objectName, objectValue).then(() => {
      this.dirty$.next(false);
    });
  }

  removeLayout() {
    if (confirm('Do you want to permanently delete this layout?')) {
      const username = this.authService.getUser()!.getName();
      const objectName = `layouts/${this.layoutName}`;
      this.storageClient.deleteObject('_global', `user.${username}`, objectName).then(() => {
        this.router.navigateByUrl(`/telemetry/layouts?instance=${this.instance.name}`);
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
