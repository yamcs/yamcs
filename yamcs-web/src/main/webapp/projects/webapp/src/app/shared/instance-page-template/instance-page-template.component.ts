import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  standalone: true,
  selector: 'app-instance-page',
  templateUrl: './instance-page-template.component.html',
  styleUrl: './instance-page-template.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancePageTemplateComponent {

  @Input()
  noscroll = false;
}
