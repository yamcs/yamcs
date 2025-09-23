import { ChangeDetectionStrategy, Component } from '@angular/core';
@Component({
  selector: 'ya-detail-toolbar-actions',
  template: '<ng-content />',
  styleUrl: './detail-toolbar-actions.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaDetailToolbarActions {}
