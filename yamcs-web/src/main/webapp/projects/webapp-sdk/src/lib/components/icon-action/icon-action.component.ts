import { ChangeDetectionStrategy, Component, HostBinding, Input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  standalone: true,
  selector: 'ya-icon-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './icon-action.component.html',
  styleUrl: './icon-action.component.css',
  imports: [
    MatIcon,
  ],
})
export class YaIconAction {

  @Input()
  icon: string;

  @Input()
  @HostBinding('class.active')
  active: boolean;

  @Input()
  @HostBinding('class.padding')
  padding = true;

  @Input()
  @HostBinding('class.disabled')
  disabled = false;
}
