import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SharedModule } from '../../shared/SharedModule';
import { OopsComponent } from '../oops/oops.component';

@Component({
  standalone: true,
  templateUrl: './server-unavailable.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    OopsComponent,
    SharedModule,
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
