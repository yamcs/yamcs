import { ChangeDetectorRef, Directive, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { AppearanceService } from '../services/appearance.service';
import { MessageService } from '../services/message.service';
import { SdkBridge } from '../services/sdk-bridge.service';
import { Synchronizer } from '../services/synchronizer.service';
import { YamcsService } from '../services/yamcs.service';

@Directive()
export abstract class BaseComponent {
  protected appearanceService: AppearanceService;
  protected changeDetection: ChangeDetectorRef;
  protected messageService: MessageService;
  protected router: Router;
  protected sdkBridge: SdkBridge;
  protected synchronizer: Synchronizer;
  protected title: Title;
  protected yamcs: YamcsService;

  constructor() {
    this.changeDetection = inject(ChangeDetectorRef);
    this.messageService = inject(MessageService);
    this.sdkBridge = inject(SdkBridge);
    this.synchronizer = inject(Synchronizer);
    this.title = inject(Title);

    this.appearanceService = this.sdkBridge.appearanceService;
    this.router = this.sdkBridge.router;
    this.yamcs = this.sdkBridge.yamcs;
  }

  setTitle(title: string) {
    this.title.setTitle(title);
  }

  openDetailPane() {
    this.appearanceService.detailPane$.next(true);
  }

  closeDetailPane() {
    this.appearanceService.detailPane$.next(false);
  }
}
