import {
  Directive,
  inject,
  Input,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfigService } from './services/config.service';
import { ExtensionService } from './services/extension.service';
import { MessageService } from './services/message.service';
import { SdkBridge } from './services/sdk-bridge.service';
import { YamcsService } from './services/yamcs.service';

@Directive()
export abstract class YamcsWebExtension implements OnChanges {
  private _extensionService: ExtensionService;
  private extensionInitialized = false;
  sdkBridge = inject(SdkBridge);

  @Input()
  public subroute: string;

  @Input()
  get extensionService() {
    return this._extensionService;
  }
  set extensionService(extensionService: ExtensionService) {
    this._extensionService = extensionService;

    // Configure bridge to use services of main webapp
    this.sdkBridge.authService = extensionService.authService;
    this.sdkBridge.appearanceService = extensionService.appearanceService;
    this.sdkBridge.router = extensionService.router;
    this.sdkBridge.yamcs = extensionService.yamcs;

    this.extensionInitialized = true;
    this.onExtensionInit();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (
      this.extensionInitialized &&
      changes['subroute'] &&
      !changes['subroute'].isFirstChange()
    ) {
      this.onExtensionChanges(this.subroute);
    }
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

  /**
   * Called when the subroute changes after initial load.
   * Services are available at this point.
   */
  onExtensionChanges(subroute: string): void {}
}
