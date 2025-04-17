import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';

@Component({
  selector: 'ya-meta',
  templateUrl: './meta.component.html',
  styleUrl: './meta.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-meta',
    '[class.ya-meta-action]': 'action()',
  },
})
export class YaMeta {
  action = input(false, { transform: booleanAttribute });
}
