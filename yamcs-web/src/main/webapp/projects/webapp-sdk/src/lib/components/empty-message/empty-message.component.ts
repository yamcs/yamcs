import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'ya-empty-message',
  templateUrl: './empty-message.component.html',
  styleUrls: ['./empty-message.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyMessageComponent {

  @Input()
  headerTitle: string;

  @Input()
  marginTop = '50px';
}
