import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostBinding, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { AuthInfo, ConfigService, ConnectionInfo, ExtensionService, PreferenceStore, SiteLink, User, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { AppearanceService } from './core/services/AppearanceService';
import { AuthService } from './core/services/AuthService';
import { SelectInstanceDialogComponent } from './shared/select-instance-dialog/select-instance-dialog.component';

@Component({
  standalone: true,
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AppComponent implements AfterViewInit, OnDestroy {

  @HostBinding('class')
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
  zenMode$: Observable<boolean>;

  userSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    router: Router,
    route: ActivatedRoute,
    private authService: AuthService,
    private preferenceStore: PreferenceStore,
    private dialog: MatDialog,
    private extensionService: ExtensionService,
    appearanceService: AppearanceService,
    private configService: ConfigService,
  ) {
    this.zenMode$ = appearanceService.zenMode$;
    this.tag = configService.getTag();
    this.authInfo = configService.getAuthInfo();
    this.siteLinks = configService.getSiteLinks();
    this.connected$ = yamcs.yamcsClient.connected$;
    this.connectionInfo$ = yamcs.connectionInfo$;
    this.user$ = authService.user$;

    this.userSubscription = this.user$.subscribe(user => {
      if (user) {
        this.showMdbItem$.next(user.hasSystemPrivilege('GetMissionDatabase'));
      } else {
        this.showMdbItem$.next(false);
      }
    });

    this.sidebar$ = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd),
      map(evt => {
        let child = route;
        while (child.firstChild) {
          child = child.firstChild;
        }

        if (child.snapshot.data && child.snapshot.data['hasSidebar'] === false) {
          return false;
        } else {
          return true;
        }
      }),
    );
  }

  ngAfterViewInit() {
    // Call custom elements named after each plugin id.
    // This allows extensions to hook some custom initialization
    // logic (for example: add to sidebar)
    var html = this.configService.getPluginIds()
      .map(id => `<${id}></${id}>`)
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

  toggleSidebar() {
    this.preferenceStore.setValue('sidebar', !this.preferenceStore.getValue('sidebar'));
  }

  logout() {
    this.authService.logout(true);
  }

  ngOnDestroy() {
    this.userSubscription?.unsubscribe();
  }
}
