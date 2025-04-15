import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';

@Component({
  selector: 'ya-instance-page',
  templateUrl: './instance-page.component.html',
  styleUrl: './instance-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaInstancePage {
  noscroll = input(false, { transform: booleanAttribute });
}
