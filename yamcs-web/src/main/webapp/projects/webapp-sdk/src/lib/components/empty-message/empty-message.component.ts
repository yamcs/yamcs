
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  standalone: true,
  selector: 'ya-empty-message',
  templateUrl: './empty-message.component.html',
  styleUrl: './empty-message.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
})
export class YaEmptyMessage {

  @Input()
  headerTitle: string;

  @Input()
  marginTop = '50px';
}
