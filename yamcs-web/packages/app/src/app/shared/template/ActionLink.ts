import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Adds a simple link with a prefixed icon. Similar to mat-button but taking
 * much less space. Part of the added value here is that icons are not
 * underlined.
 */
@Component({
  selector: 'app-action-link',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ActionLink.html',
  styleUrls: ['./ActionLink.css'],
})
export class ActionLink {

  @Input()
  icon: string;

  @Input()
  link: string;
}
