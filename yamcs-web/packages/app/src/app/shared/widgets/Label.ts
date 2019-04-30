import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-label',
  templateUrl: './Label.html',
  styleUrls: ['./Label.css'],
})
export class Label {

  @Input()
  icon: string;

  @Input()
  backgroundColor = '#eee';

  @Input()
  color = 'inherit';

  @Input()
  borderColor = 'transparent';
}
