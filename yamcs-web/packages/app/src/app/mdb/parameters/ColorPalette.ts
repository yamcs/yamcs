import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import * as paletteNs from 'google-palette';

// This is a workaround for ng-packagr. I don't know why.
// https://github.com/dherges/ng-packagr/issues/217
const palette = paletteNs;

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
