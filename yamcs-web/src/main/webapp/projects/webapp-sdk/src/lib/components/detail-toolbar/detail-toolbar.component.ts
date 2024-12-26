import { booleanAttribute, Component, input } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { MatTooltip } from '@angular/material/tooltip';
import { BaseComponent } from '../../abc/BaseComponent';
import { YaIconAction } from '../icon-action/icon-action.component';

@Component({
  standalone: true,
  selector: 'ya-detail-toolbar',
  templateUrl: './detail-toolbar.component.html',
  styleUrl: './detail-toolbar.component.css',
  imports: [
    MatToolbar,
    MatTooltip,
    YaIconAction,
  ],
})
export class YaDetailToolbar extends BaseComponent {

  alwaysOpen = input(false, { transform: booleanAttribute });
}
