import {
  ChangeDetectionStrategy,
  Component,
  Input,
} from '@angular/core';

@Component({
  selector: 'app-system-toolbar',
  templateUrl: './system-toolbar.component.html',
  styleUrls: ['./system-toolbar.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemToolbarComponent {

  @Input()
  header: string;
}
