import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { Instance } from '../../../yamcs-client';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../store/instance.selectors';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {

  title = 'Yamcs';

  instance$: Observable<Instance>;

  constructor(store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);
  }
}
