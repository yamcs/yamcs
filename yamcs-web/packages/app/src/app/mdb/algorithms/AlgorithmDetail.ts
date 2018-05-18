import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, OnDestroy, ViewChild } from '@angular/core';
import { Algorithm, Instance } from '@yamcs/client';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/mode/python';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { Subscription } from 'rxjs';
import { PreferenceStore } from '../../core/services/PreferenceStore';

@Component({
  selector: 'app-algorithm-detail',
  templateUrl: './AlgorithmDetail.html',
  styleUrls: ['./AlgorithmDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmDetail implements AfterViewInit, OnDestroy {

  @ViewChild('text')
  textContainer: ElementRef;

  @Input()
  instance: Instance;

  @Input()
  algorithm: Algorithm;

  @Input()
  readonly = false;

  private editor: ace.Editor;

  private darkModeSubscription: Subscription;

  constructor(private preferenceStore: PreferenceStore) {
  }

  ngAfterViewInit() {
    this.editor = ace.edit(this.textContainer.nativeElement);
    this.editor.setReadOnly(this.readonly);

    switch (this.algorithm.language.toLowerCase()) {
      case 'javascript':
        this.editor.getSession().setMode('ace/mode/javascript');
        break;
      case 'python':
        this.editor.getSession().setMode('ace/mode/javascript');
        break;
      default:
        console.warn(`Unexpected language ${this.algorithm.language}`);
    }

    this.applyTheme(this.preferenceStore.isDarkMode());
    this.darkModeSubscription = this.preferenceStore.darkMode$.subscribe(darkMode => {
      this.applyTheme(darkMode);
    });
  }

  private applyTheme(dark: boolean) {
    if (dark) {
      this.editor.setTheme('ace/theme/twilight');
    } else {
      this.editor.setTheme('ace/theme/eclipse');
    }
  }

  ngOnDestroy() {
    if (this.darkModeSubscription) {
      this.darkModeSubscription.unsubscribe();
    }
  }
}
