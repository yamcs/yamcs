import { Component, ChangeDetectionStrategy, HostListener, Inject, Input } from '@angular/core';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { DOCUMENT } from '@angular/platform-browser';
import { Observable } from 'rxjs/Observable';
import { Instance } from '../../../yamcs-client';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../store/instance.selectors';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    trigger('shadowy', [
      state('inactive', style({
        boxShadow: 'none'
      })),
      state('active', style({
        boxShadow: '0 .125rem .3125rem rgba(0,0,0,.26)'
      })),
      transition('inactive => active', animate('200ms ease-in')),
      transition('active => inactive', animate('200ms ease-out'))
    ])
  ],
})
export class AppComponent {

  title = 'Yamcs';

  @Input()
  shadowy = 'inactive';

  instance$: Observable<Instance>;

  constructor(
    @Inject(DOCUMENT) private document: Document,
    store: Store<State>,
  ) {
    this.instance$ = store.select(selectCurrentInstance);
  }

  @HostListener('window:scroll', [])
  onScroll() {
    // https://developer.mozilla.org/en/docs/Web/API/document/scrollingElement
    const scrollingElement = this.document.scrollingElement || this.document.documentElement;
    if (scrollingElement.scrollTop > 0) {
      this.shadowy = 'active';
    } else {
      this.shadowy = 'inactive';
    }
  }
}
