import { Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { NamedParameterValue } from '../stack-file/StackedEntry';

@Component({
  selector: 'app-verify-icon',
  templateUrl: './verify-icon.component.html',
  styleUrl: './verify-icon.component.css',
  imports: [WebappSdkModule],
})
export class VerifyIconComponent {
  @Input()
  comparison: NamedParameterValue;
}
