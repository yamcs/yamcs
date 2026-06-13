import { Component, Input } from '@angular/core';

@Component({
  selector: 'ya-empty-message',
  templateUrl: './empty-message.component.html',
  styleUrl: './empty-message.component.css',
  imports: [],
})
export class YaEmptyMessage {
  @Input()
  headerTitle: string;

  @Input()
  marginTop = '50px';
}
