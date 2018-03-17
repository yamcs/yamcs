import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { YamcsService } from '../../core/services/YamcsService';
import { Instance, Processor } from '@yamcs/client';
import { Observable } from 'rxjs/Observable';
import { ActivatedRoute } from '@angular/router';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: 'ProcessorPage.html',
  styleUrls: ['./ProcessorPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorPage implements OnInit {

  instance$: Observable<Instance>;
  processor$: Promise<Processor>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>, title: Title) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name + ' - Yamcs');
    this.processor$ = yamcs.getSelectedInstance().getProcessor(name);
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
