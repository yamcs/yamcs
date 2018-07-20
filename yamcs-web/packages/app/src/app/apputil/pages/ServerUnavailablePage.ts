import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  templateUrl: './ServerUnavailablePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServerUnavailablePage implements OnInit {

  page: string | null;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.page = this.route.snapshot.queryParamMap.get('page');
  }
}
