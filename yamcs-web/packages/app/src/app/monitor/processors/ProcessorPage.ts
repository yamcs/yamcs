import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Instance, Processor } from '@yamcs/client';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: 'ProcessorPage.html',
  styleUrls: ['./ProcessorPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorPage {

  instance: Instance;
  processor$: Promise<Processor>;

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
    title: Title,
    private authService: AuthService,
  ) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name + ' - Yamcs');
    this.processor$ = yamcs.getInstanceClient()!.getProcessor(name);
    this.instance = yamcs.getInstance();
  }

  mayControlCommandQueue() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlCommandQueue');
  }
}
