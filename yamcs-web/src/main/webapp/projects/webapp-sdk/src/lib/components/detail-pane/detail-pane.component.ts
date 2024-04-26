import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  standalone: true,
  selector: 'ya-detail-pane',
  templateUrl: './detail-pane.component.html',
  styleUrl: './detail-pane.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaDetailPane {
}
