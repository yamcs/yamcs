import { NgIf } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  standalone: true,
  selector: 'ya-label',
  templateUrl: './label.component.html',
  styleUrl: './label.component.css',
  imports: [
    MatIcon,
    NgIf,
  ],
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
