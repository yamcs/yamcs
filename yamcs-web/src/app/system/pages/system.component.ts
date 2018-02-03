import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { SelectInstanceAction } from '../../core/store/instance.actions';
import { Observable } from 'rxjs/Observable';
import { Instance } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './system.component.html',
  styleUrls: ['./system.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemPageComponent implements OnInit {

  instance$: Observable<Instance>;

  constructor(route: ActivatedRoute, private store: Store<State>) {
    const instanceName = route.snapshot.paramMap.get('instance');
    if (instanceName !== null) {
      store.dispatch(new SelectInstanceAction(instanceName));
    }
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
