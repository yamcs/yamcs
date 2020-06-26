import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  templateUrl: './NotFoundPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage implements OnInit {

  page: string | null;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.page = this.route.snapshot.queryParamMap.get('page');
  }
}
