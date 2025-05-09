import { CdkPortalOutlet } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  ContentChild,
  input,
} from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import {
  APP_ADMIN_TOOLBAR,
  AppAdminToolbarLabel,
} from './admin-toolbar-label.directive';

@Component({
  selector: 'app-admin-toolbar',
  templateUrl: './admin-toolbar.component.html',
  styleUrl: './admin-toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: APP_ADMIN_TOOLBAR,
      useExisting: AppAdminToolbar,
    },
  ],
  host: {
    class: 'app-instance-toolbar',
  },
  imports: [CdkPortalOutlet, WebappSdkModule],
})
export class AppAdminToolbar {
  // Plain text label, used when there is no template label
  textLabel = input<string | undefined>(undefined, { alias: 'label' });

  private _templateLabel: AppAdminToolbarLabel;

  // Content for the attr label given by `<ng-template app-admin-toolbar-label>`
  @ContentChild(AppAdminToolbarLabel)
  get templateLabel(): AppAdminToolbarLabel {
    return this._templateLabel;
  }
  set templateLabel(value: AppAdminToolbarLabel | undefined) {
    if (value && value._closestToolbar === this) {
      this._templateLabel = value;
    }
  }
}
