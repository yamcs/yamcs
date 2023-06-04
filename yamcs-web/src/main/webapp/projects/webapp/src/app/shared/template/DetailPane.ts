import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-detail-pane',
  templateUrl: './DetailPane.html',
  styleUrls: ['./DetailPane.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailPane {
}
