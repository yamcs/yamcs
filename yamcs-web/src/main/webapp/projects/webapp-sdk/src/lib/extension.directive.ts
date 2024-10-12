import { Directive, inject, Input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfigService } from './services/config.service';
import { ExtensionService } from './services/extension.service';
import { MessageService } from './services/message.service';
import { SdkBridge } from './services/sdk-bridge.service';
import { YamcsService } from './services/yamcs.service';

@Directive()
export abstract class YamcsWebExtension {

  private _extensionService: ExtensionService;
  sdkBridge = inject(SdkBridge);

  @Input()
  public subroute: string;

  @Input()
  get extensionService() { return this._extensionService; }
  set extensionService(extensionService: ExtensionService) {
    this._extensionService = extensionService;

    // Configure bridge to use router of main webapp.
    this.sdkBridge.router = extensionService.router;

    this.onExtensionInit();
  }

  get configService(): ConfigService {
    return this.extensionService?.configService;
  }

  get messageService(): MessageService {
    return this.extensionService?.messageService;
  }

  get router(): Router {
    return this.extensionService?.router;
  }

  get route(): ActivatedRoute {
    return this.extensionService?.route;
  }

  get yamcs(): YamcsService {
    return this.extensionService?.yamcs;
  }

  /**
   * Called when the extension is initialized in the main application.
   * At this time, main services are available.
   */
  abstract onExtensionInit(): void;
}
