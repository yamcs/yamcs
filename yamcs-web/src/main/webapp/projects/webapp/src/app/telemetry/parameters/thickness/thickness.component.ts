import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
  numberAttribute,
} from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-thickness',
  templateUrl: './thickness.component.html',
  styleUrl: './thickness.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ThicknessComponent {
  options = [1, 2, 3, 4];

  @Input({ transform: numberAttribute })
  selectedThickness = 2;

  @Input()
  color: string;

  constructor(private changeDetection: ChangeDetectorRef) {}

  select(thickness: number) {
    this.selectedThickness = thickness;
  }

  changeColor(color: string) {
    this.color = color;
    this.changeDetection.detectChanges();
  }
}
