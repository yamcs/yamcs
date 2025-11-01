import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-form-content',
  templateUrl: './form-content.component.html',
  styleUrl: './form-content.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-form-content ya-form',
  },
})
export class YaFormContent {}
