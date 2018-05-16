import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';

@Component({
  selector: 'app-thickness',
  templateUrl: './Thickness.html',
  styleUrls: ['./Thickness.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Thickness {

  options = [1, 2, 3, 4];

  @Input()
  selectedThickness = 2;

  @Input()
  color: string;

  constructor(private changeDetection: ChangeDetectorRef) {
  }

  select(thickness: number) {
    this.selectedThickness = thickness;
  }

  changeColor(color: string) {
    this.color = color;
    this.changeDetection.detectChanges();
  }
}
