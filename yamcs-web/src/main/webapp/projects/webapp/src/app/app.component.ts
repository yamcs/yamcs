import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  Signal,
  ViewChild,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import {
  AppearanceService,
  AuthInfo,
  AuthService,
  ConfigService,
  ConnectionInfo,
  ExtensionService,
  Formatter,
  Preferences,
  SiteLink,
  Theme,
  User,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, map, shareReplay, tap } from 'rxjs/operators';
import { SearchInputComponent } from './appbase/search-input/search-input.component';
import { SelectInstanceDialogComponent } from './shared/select-instance-dialog/select-instance-dialog.component';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'componentCssClass',
  },
  imports: [SearchInputComponent, WebappSdkModule],
})
export class AppComponent implements AfterViewInit, OnDestroy {
  componentCssClass: string;

  @ViewChild('extensionInitializers')
  extensionInitializersRef: ElementRef<HTMLDivElement>;

  title = 'Yamcs';
  tag: string;
  authInfo: AuthInfo;
  siteLinks: SiteLink[];

  connectionInfo$: Observable<ConnectionInfo | null>;
  connected$: Observable<boolean>;
  user$: Observable<User | null>;

  showMdbItem$ = new BehaviorSubject<boolean>(false);
  sidebar$: Observable<boolean>;
  section$: Observable<string | null>;
  focusMode$: Observable<boolean>;
  utc: Signal<boolean>;
  themePreference = this.appearanceService.themePreference;

  userSubscription: Subscription;

  // Incomplete: for dev use only
  enableAppearanceBeta = false;

  constructor(
    private yamcs: YamcsService,
    router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
    private prefs: Preferences,
    private dialog: MatDialog,
    private extensionService: ExtensionService,
    private appearanceService: AppearanceService,
    private configService: ConfigService,
    private formatter: Formatter,
  ) {
    this.focusMode$ = appearanceService.focusMode$;
    this.tag = configService.getTag();
    this.authInfo = configService.getAuthInfo();
    this.siteLinks = configService.getSiteLinks();
    this.connected$ = yamcs.yamcsClient.connected$;
    this.connectionInfo$ = yamcs.connectionInfo$;
    this.user$ = authService.user$;
    this.utc = formatter.utc;

    this.userSubscription = this.user$.subscribe((user) => {
      if (user) {
        this.showMdbItem$.next(user.hasSystemPrivilege('GetMissionDatabase'));
      } else {
        this.showMdbItem$.next(false);
      }
    });

    const route$ = router.events.pipe(
      filter((evt) => evt instanceof NavigationEnd),
      tap(() => {
        // Emit ActivatedRoute updates for use in webcomponents
        window.dispatchEvent(
          new CustomEvent('YA_ACTIVATED_ROUTE', {
            detail: { route },
          }),
        );
      }),
      map(() => {
        let child = route;
        while (child.firstChild) {
          child = child.firstChild;
        }

        return child.snapshot;
      }),
      // Avoid multiple execution of our tap effect
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    this.sidebar$ = route$.pipe(
      map((snapshot) => snapshot.data?.['hasSidebar'] !== false),
    );
    this.section$ = route$.pipe(
      map((activatedRoute) => activatedRoute.data?.['section'] ?? null),
    );
  }

  ngAfterViewInit() {
    // Call custom elements named after each plugin id.
    // This allows extensions to hook some custom initialization
    // logic (for example: add to sidebar)
    var html = this.configService
      .getPluginIds()
      .map((id) => `<${id}></${id}>`)
      .join('');
    this.extensionInitializersRef.nativeElement.innerHTML = html;
    var childNodes = this.extensionInitializersRef.nativeElement.childNodes;
    for (let i = 0; i < childNodes.length; i++) {
      (childNodes[i] as any).extensionService = this.extensionService;
    }
  }

  openInstanceDialog() {
    this.dialog.open(SelectInstanceDialogComponent, {
      width: '650px',
      panelClass: ['no-padding-dialog'],
    });
  }

  setUTC(utc: boolean) {
    if (this.formatter.utc() !== utc) {
      this.prefs.setBoolean('utc', utc);
      this.yamcs.switchContext(this.yamcs.instance, this.yamcs.processor);
    }
  }

  setTheme(theme: Theme) {
    this.appearanceService.setTheme(theme);
  }

  logout() {
    this.authService.logout(true);
  }

  ngOnDestroy() {
    this.userSubscription?.unsubscribe();
  }
}
