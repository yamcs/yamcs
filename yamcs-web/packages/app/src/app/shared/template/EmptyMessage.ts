import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-empty-message',
  templateUrl: './EmptyMessage.html',
  styleUrls: ['./EmptyMessage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyMessage {

  @Input()
  headerTitle: string;
}
