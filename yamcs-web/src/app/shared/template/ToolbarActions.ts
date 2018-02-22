import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-toolbar-actions',
  template: '<ng-content></ng-content>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToolbarActions {
}
