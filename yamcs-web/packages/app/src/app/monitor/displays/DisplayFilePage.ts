import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance } from '@yamcs/client';
import { Display, DisplayHolder, OpenDisplayCommandOptions, OpiDisplay, ParDisplay, UssDisplay } from '@yamcs/displays';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/mode/python';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { BehaviorSubject, Subscription } from 'rxjs';
import * as screenfull from 'screenfull';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from './MyDisplayCommunicator';

@Component({
  templateUrl: './DisplayFilePage.html',
  styleUrls: ['./DisplayFilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFilePage implements DisplayHolder, AfterViewInit, OnDestroy {

  instance: Instance;

  @ViewChild('viewerContainer')
  viewerContainer: ElementRef;

  path: string;
  filename: string;
  folderLink: string;

  fullscreen$ = new BehaviorSubject<boolean>(false);
  fullscreenListener: () => void;

  private editor: ace.Editor;

  private darkModeSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
    private route: ActivatedRoute,
    private preferenceStore: PreferenceStore,
  ) {
    this.instance = yamcs.getInstance();
    this.fullscreenListener = () => this.fullscreen$.next(screenfull.isFullscreen);
    screenfull.on('change', this.fullscreenListener);

    const url = this.route.snapshot.url;
    let path = '';
    for (let i = 0; i < url.length; i++) {
      if (i === url.length - 1) {
        this.filename = url[i].path;
        this.folderLink = '/monitor/displays/browse' + path;
      }
      path += '/' + url[i].path;
    }
    this.path = path;
  }



  ngAfterViewInit() {
    const container: HTMLDivElement = this.viewerContainer.nativeElement;
    container.className = 'vcenter';
    const instance = this.instance.name;
    const url = `${this.yamcs.yamcsClient.staticUrl}/${instance}/displays${this.path}`;

    if (this.filename.toLowerCase().endsWith('.uss')) {
      this.loadDisplay(container, 'USS');
    } else if (this.filename.toLowerCase().endsWith('.opi')) {
      this.loadDisplay(container, 'OPI');
    } else if (this.filename.toLowerCase().endsWith('.par')) {
      this.loadDisplay(container, 'PAR');
    } else if (this.isImage()) {
      const imgEl = document.createElement('img');
      imgEl.setAttribute('src', url);
      container.appendChild(imgEl);
    } else if (this.filename.toLocaleLowerCase().endsWith('.js')) {
      container.className = '';
      this.yamcs.yamcsClient.getStaticText(`${instance}/displays${this.path}`).then(text => {
        container.style.lineHeight = '16px';
        container.style.textAlign = 'left';
        container.style.height = '100%';
        container.innerHTML = text;
        this.editor = ace.edit(container);
        this.editor.setReadOnly(false);
        this.editor.getSession().setMode('ace/mode/javascript');
      });
    } else {
      const preEl = document.createElement('pre');
      preEl.style.lineHeight = '16px';
      preEl.style.margin = '1em';
      preEl.style.textAlign = 'left';
      this.yamcs.yamcsClient.getStaticText(`${instance}/displays${this.path}`).then(text => {
        preEl.innerHTML = text;
        container.appendChild(preEl);
      });
    }

    this.applyTheme(this.preferenceStore.isDarkMode());
    this.darkModeSubscription = this.preferenceStore.darkMode$.subscribe(darkMode => {
      this.applyTheme(darkMode);
    });
  }

  private isImage() {
    const lc = this.filename.toLowerCase();
    return lc.endsWith('.png') || lc.endsWith('.gif' || lc.endsWith('.jpg') || lc.endsWith('jpeg') || lc.endsWith('bmp'));
  }

  private loadDisplay(container: HTMLDivElement, type: 'OPI' | 'PAR' | 'USS') {

    const displayCommunicator = new MyDisplayCommunicator(this.yamcs, this.router);
    let display: Display;
    switch (type) {
      case 'OPI':
        display = new OpiDisplay(this, container, displayCommunicator);
        break;
      case 'PAR':
        display = new ParDisplay(this, container, displayCommunicator);
        break;
      case 'USS':
        display = new UssDisplay(this, container, displayCommunicator);
        break;
      default:
        throw new Error('Unexpected display type ' + type);
    }
    display.parseAndDraw(this.path).then(() => {
      const ids = display!.getParameterIds();
      if (ids.length) {
        this.yamcs.getInstanceClient()!.getParameterValueUpdates({
          id: ids,
          abortOnInvalid: false,
          sendFromCache: true,
          updateOnExpiration: true,
        }).then(res => {
          res.parameterValues$.subscribe(pvals => {
            display!.processParameterValues(pvals);
          });
        });
      }
    });
  }

  private applyTheme(dark: boolean) {
    if (this.editor) {
      if (dark) {
        this.editor.setTheme('ace/theme/twilight');
      } else {
        this.editor.setTheme('ace/theme/eclipse');
      }
    }
  }

  goFullscreen() {
    if (screenfull.enabled) {
      screenfull.request(this.viewerContainer.nativeElement);
    } else {
      alert('Your browser does not appear to support going full screen');
    }
  }

  getBaseId() { // DisplayHolder
    return this.path;
  }

  openDisplay(options: OpenDisplayCommandOptions) { // DisplayHolder
    // TODO (called via e.g. NavigationButton)
  }

  closeDisplay() { // DisplayHolder
    // NOP
  }

  ngOnDestroy() {
    screenfull.off('change', this.fullscreenListener);
    if (this.darkModeSubscription) {
      this.darkModeSubscription.unsubscribe();
    }
  }
}
