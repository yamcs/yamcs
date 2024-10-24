import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  standalone: true,
  selector: 'ya-detail-pane',
  templateUrl: './detail-pane.component.html',
  styleUrl: './detail-pane.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatButtonModule, MatTooltipModule],
})
export class YaDetailPane {
  @Input() collapsed = false;

  toggleCollapsed() {
    this.collapsed = !this.collapsed;
  }
}
