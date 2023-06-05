import { ChangeDetectionStrategy, Component, HostBinding, Input } from '@angular/core';

@Component({
  selector: 'ya-text-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './text-action.component.html',
  styleUrls: ['./text-action.component.css'],
})
export class TextActionComponent {

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
