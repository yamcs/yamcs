import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-toolbar-actions',
  template: '<ng-content />',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToolbarActionsComponent {
}
