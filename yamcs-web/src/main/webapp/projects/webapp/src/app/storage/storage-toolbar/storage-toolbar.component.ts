import { CdkPortalOutlet } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  ContentChild,
  input,
} from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import {
  APP_STORAGE_TOOLBAR,
  AppStorageToolbarLabel,
} from './storage-toolbar-label.directive';

@Component({
  selector: 'app-storage-toolbar',
  templateUrl: './storage-toolbar.component.html',
  styleUrl: './storage-toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: APP_STORAGE_TOOLBAR,
      useExisting: AppStorageToolbar,
    },
  ],
  host: {
    class: 'app-storage-toolbar',
  },
  imports: [CdkPortalOutlet, WebappSdkModule],
})
export class AppStorageToolbar {
  // Plain text label, used when there is no template label
  textLabel = input<string | undefined>(undefined, { alias: 'label' });

  private _templateLabel: AppStorageToolbarLabel;

  // Content for the attr label given by `<ng-template app-storage-toolbar-label>`
  @ContentChild(AppStorageToolbarLabel)
  get templateLabel(): AppStorageToolbarLabel {
    return this._templateLabel;
  }
  set templateLabel(value: AppStorageToolbarLabel | undefined) {
    if (value && value._closestToolbar === this) {
      this._templateLabel = value;
    }
  }
}
