import { Component, Input } from '@angular/core';

@Component({
  selector: 'ya-label',
  templateUrl: './label.component.html',
  styleUrls: ['./label.component.css'],
})
export class LabelComponent {

  @Input()
  icon: string;

  @Input()
  backgroundColor = '#eee';

  @Input()
  color = 'inherit';

  @Input()
  borderColor = 'transparent';
}
