import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { Observable } from 'rxjs/Observable';
import { Instance } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './mdb.page.html',
  styleUrls: ['./mdb.page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MdbPageComponent implements OnInit {

  instance$: Observable<Instance>;

  constructor(private store: Store<State>) {
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
