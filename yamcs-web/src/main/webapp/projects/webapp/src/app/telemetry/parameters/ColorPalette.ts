import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

// Generated with node:
//
// palette = require('google-palette');
// palette('mpn65', 30);
const PALETTE = [
  '#ff0029', '#377eb8', '#66a61e',
  '#984ea3', '#00d2d5', '#ff7f00',
  '#af8d00', '#7f80cd', '#b3e900',
  '#c42e60', '#a65628', '#f781bf',
  '#8dd3c7', '#bebada', '#fb8072',
  '#80b1d3', '#fdb462', '#fccde5',
  '#bc80bd', '#ffed6f', '#c4eaff',
  '#cf8c00', '#1b9e77', '#d95f02',
  '#e7298a', '#e6ab02', '#a6761d',
  '#0097ff', '#00d067', '#000000'
];

@Component({
  selector: 'app-color-palette',
  templateUrl: './ColorPalette.html',
  styleUrls: ['./ColorPalette.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ColorPalette {

  @Input()
  selectedColor = PALETTE[Math.floor(Math.random() * PALETTE.length)];

  @Output()
  select = new EventEmitter<string>();

  // Expose to template
  colors = PALETTE;

  doSelect(color: string) {
    this.select.emit(color);
    this.selectedColor = color;
  }
}
