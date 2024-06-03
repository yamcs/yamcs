import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-thickness',
  templateUrl: './thickness.component.html',
  styleUrl: './thickness.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ThicknessComponent {

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
