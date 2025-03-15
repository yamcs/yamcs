import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-stepper',
  templateUrl: './stepper.component.html',
  styleUrl: './stepper.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaStepper {}
