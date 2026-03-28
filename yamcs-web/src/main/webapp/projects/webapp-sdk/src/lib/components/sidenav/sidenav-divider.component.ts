import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-sidenav-divider',
  template: '',
  styleUrl: './sidenav-divider.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-sidenav-divider',
  },
})
export class YaSidenavDivider {}
