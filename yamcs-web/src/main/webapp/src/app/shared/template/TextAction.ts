import { ChangeDetectionStrategy, Component, HostBinding, Input } from '@angular/core';

@Component({
  selector: 'app-text-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './TextAction.html',
  styleUrls: ['./TextAction.css'],
})
export class TextAction {

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
