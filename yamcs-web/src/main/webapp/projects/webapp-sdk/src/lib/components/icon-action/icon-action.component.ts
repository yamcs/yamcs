import { ChangeDetectionStrategy, Component, HostBinding, Input } from '@angular/core';

@Component({
  selector: 'ya-icon-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './icon-action.component.html',
  styleUrls: ['./icon-action.component.css'],
})
export class IconActionComponent {

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
