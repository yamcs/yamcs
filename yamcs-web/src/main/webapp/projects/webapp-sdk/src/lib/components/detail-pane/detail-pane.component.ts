import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-detail-pane',
  templateUrl: './detail-pane.component.html',
  styleUrls: ['./detail-pane.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailPaneComponent {
}
