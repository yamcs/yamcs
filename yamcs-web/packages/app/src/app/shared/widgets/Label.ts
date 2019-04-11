import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-label',
  templateUrl: './Label.html',
  styleUrls: ['./Label.css'],
})
export class Label {

  @Input()
  icon: string;
}
