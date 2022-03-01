import { ChangeDetectionStrategy, Component, HostBinding, Input } from '@angular/core';

@Component({
  selector: 'app-icon-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './IconAction.html',
  styleUrls: ['./IconAction.css'],
})
export class IconAction {

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
