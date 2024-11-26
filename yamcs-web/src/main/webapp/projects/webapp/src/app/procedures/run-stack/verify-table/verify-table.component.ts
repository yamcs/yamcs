import { Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { StackedVerifyEntry } from '../stack-file/StackedEntry';
import { VerifyIconComponent } from '../verify-icon/verify-icon.component';

@Component({
  standalone: true,
  selector: 'app-verify-table',
  templateUrl: './verify-table.component.html',
  styleUrl: './verify-table.component.css',
  //changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    VerifyIconComponent,
    WebappSdkModule,
  ],
})
export class VerifyTableComponent {

  @Input()
  entry: StackedVerifyEntry;
}
