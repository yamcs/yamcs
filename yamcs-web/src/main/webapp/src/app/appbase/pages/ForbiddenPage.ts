import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  templateUrl: './ForbiddenPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenPage implements OnInit {

  page: string | null;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.page = this.route.snapshot.queryParamMap.get('page');
  }
}
