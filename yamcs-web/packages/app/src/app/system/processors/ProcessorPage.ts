import { Component, ChangeDetectionStrategy } from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { Instance, Processor } from '@yamcs/client';
import { ActivatedRoute } from '@angular/router';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: 'ProcessorPage.html',
  styleUrls: ['./ProcessorPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorPage {

  instance: Instance;
  processor$: Promise<Processor>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, title: Title) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name + ' - Yamcs');
    this.processor$ = yamcs.getSelectedInstance().getProcessor(name);
    this.instance = yamcs.getInstance();
  }
}
