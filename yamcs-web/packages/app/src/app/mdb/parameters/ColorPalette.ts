import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import * as palette from 'google-palette';

@Component({
  selector: 'app-color-palette',
  templateUrl: './ColorPalette.html',
  styleUrls: ['./ColorPalette.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ColorPalette {

  colors: string[] = palette('mpn65', 30).map((c: string) => '#' + c);

  @Input()
  selectedColor = this.colors[Math.floor(Math.random() * this.colors.length)];

  @Output()
  select = new EventEmitter<string>();

  doSelect(color: string) {
    this.select.emit(color);
    this.selectedColor = color;
  }
}
