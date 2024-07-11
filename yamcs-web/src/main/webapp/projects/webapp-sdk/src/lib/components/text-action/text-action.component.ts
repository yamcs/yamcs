
import { ChangeDetectionStrategy, Component, HostBinding, Input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  standalone: true,
  selector: 'ya-text-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './text-action.component.html',
  styleUrl: './text-action.component.css',
  imports: [
    MatIcon
],
})
export class YaTextAction {

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
