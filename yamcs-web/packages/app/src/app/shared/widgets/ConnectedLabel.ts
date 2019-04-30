import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-connected-label',
  templateUrl: './ConnectedLabel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConnectedLabel {

  @Input()
  connected?: boolean;

  @Input()
  tag?: string;
}
