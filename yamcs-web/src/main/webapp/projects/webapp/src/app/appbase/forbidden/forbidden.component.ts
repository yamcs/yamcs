import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SharedModule } from '../../shared/SharedModule';
import { OopsComponent } from '../oops/oops.component';

@Component({
  standalone: true,
  templateUrl: './forbidden.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    OopsComponent,
    SharedModule,
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
