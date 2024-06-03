import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { OopsComponent } from '../oops/oops.component';

@Component({
  standalone: true,
  templateUrl: './server-unavailable.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    OopsComponent,
    WebappSdkModule,
  ],
})
export class ServerUnavailableComponent implements OnInit {

  next: string | null;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.next = this.route.snapshot.queryParamMap.get('next');
  }
}
