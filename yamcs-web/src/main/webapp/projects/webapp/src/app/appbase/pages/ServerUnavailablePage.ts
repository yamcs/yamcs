import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  templateUrl: './ServerUnavailablePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServerUnavailablePage implements OnInit {

  next: string | null;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.next = this.route.snapshot.queryParamMap.get('next');
  }
}
