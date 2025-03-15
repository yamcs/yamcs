import { Component, Input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-label',
  templateUrl: './label.component.html',
  styleUrl: './label.component.css',
  imports: [MatIcon],
})
export class YaLabel {
  @Input()
  icon: string;

  @Input()
  backgroundColor = '#eee';

  @Input()
  color = 'inherit';

  @Input()
  borderColor = '#ccc';
}
