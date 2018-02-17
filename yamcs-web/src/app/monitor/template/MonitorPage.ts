import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { Observable } from 'rxjs/Observable';
import { Instance } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './MonitorPage.html',
  styleUrls: ['./MonitorPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorPage implements OnInit {

  instance$: Observable<Instance>;

  constructor(private store: Store<State>) {
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
