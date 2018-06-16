import { ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance } from '@yamcs/client';
import { LayoutState } from '@yamcs/displays';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';
import { LayoutComponent } from '../displays/LayoutComponent';

@Component({
  templateUrl: './LayoutPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutPage {

  @ViewChild('layoutComponent')
  private layoutComponent: LayoutComponent;

  instance: Instance;

  layoutName: string;
  layout$: Promise<LayoutState>;

  dirty$ = new BehaviorSubject<boolean>(false);

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

    this.layout$ = new Promise<LayoutState>((resolve, reject) => {
      const username = authService.getUser()!.getUsername();
      const objectName = 'layouts/' + this.layoutName;
      yamcs.getInstanceClient()!.getObject(`user.${username}` /* FIXME */, objectName).then(response => {
        response.json().then(layoutState => resolve(layoutState)).catch(err => reject(err));
      }).catch(err => reject(err));
    });
  }

  saveLayout() {
    const username = this.authService.getUser()!.getUsername();
    const state = this.layoutComponent.getLayoutState();
    const objectName = `layouts/${this.layoutName}`;
    const objectValue = new Blob([JSON.stringify(state)], {
      type: 'application/json',
    });
    this.yamcs.getInstanceClient()!.uploadObject(`user.${username}`, objectName, objectValue).then(() => {
      this.dirty$.next(false);
    });
  }

  renameLayout() {
    // TODO
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
}
