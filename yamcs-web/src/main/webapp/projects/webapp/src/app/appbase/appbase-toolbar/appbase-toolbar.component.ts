import { CdkPortalOutlet } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  ContentChild,
  input,
} from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import {
  APP_APPBASE_TOOLBAR,
  AppAppBaseToolbarLabel,
} from './appbase-toolbar-label.directive';

@Component({
  selector: 'app-appbase-toolbar',
  templateUrl: './appbase-toolbar.component.html',
  styleUrl: './appbase-toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: APP_APPBASE_TOOLBAR,
      useExisting: AppAppBaseToolbar,
    },
  ],
  host: {
    class: 'app-appbase-toolbar',
  },
  imports: [CdkPortalOutlet, WebappSdkModule],
})
export class AppAppBaseToolbar {
  // Plain text label, used when there is no template label
  textLabel = input<string | undefined>(undefined, { alias: 'label' });

  private _templateLabel: AppAppBaseToolbarLabel;

  // Content for the attr label given by `<ng-template app-appbase-toolbar-label>`
  @ContentChild(AppAppBaseToolbarLabel)
  get templateLabel(): AppAppBaseToolbarLabel {
    return this._templateLabel;
  }
  set templateLabel(value: AppAppBaseToolbarLabel | undefined) {
    if (value && value._closestToolbar === this) {
      this._templateLabel = value;
    }
  }
}
