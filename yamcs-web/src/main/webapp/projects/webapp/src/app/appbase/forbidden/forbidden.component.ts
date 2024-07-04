import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { OopsComponent } from '../oops/oops.component';

@Component({
  standalone: true,
  templateUrl: './forbidden.component.html',
  styleUrl: './forbidden.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    OopsComponent,
    WebappSdkModule,
  ],
})
export class ForbiddenComponent implements OnInit {

  page: string | null;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.page = this.route.snapshot.queryParamMap.get('page');
  }
}
