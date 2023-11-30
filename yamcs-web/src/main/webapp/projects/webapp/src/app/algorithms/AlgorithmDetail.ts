import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Algorithm, AlgorithmOverrides, AlgorithmStatus, MessageService, YamcsService } from '@yamcs/webapp-sdk';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/mode/python';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../core/services/AuthService';

@Component({
  selector: 'app-algorithm-detail',
  templateUrl: './AlgorithmDetail.html',
  styleUrls: ['./AlgorithmDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmDetail implements AfterViewInit {

  @ViewChild('text', { static: false })
  textContainer: ElementRef;

  @Input()
  algorithm: Algorithm;

  @Input()
  status: AlgorithmStatus | null;

  overrides$ = new BehaviorSubject<AlgorithmOverrides | null>(null);

  private editor: ace.Editor;
  dirty$ = new BehaviorSubject<boolean>(false);

  constructor(
    private route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private authService: AuthService,
  ) { }

  ngAfterViewInit() {
    if (this.algorithm.text) {
      this.initializeEditor();
      this.refreshOverrides();
    }
  }

  isChangeMissionDatabaseEnabled() {
    const user = this.authService.getUser()!;
    return user.hasSystemPrivilege('ChangeMissionDatabase');
  }

  private initializeEditor() {
    this.editor = ace.edit(this.textContainer.nativeElement);
    this.editor.$blockScrolling = Infinity; // Required to suppress a warning

    if (this.isChangeMissionDatabaseEnabled()) {
      this.editor.addEventListener('change', () => {
        this.dirty$.next(true);
      });
    } else {
      this.editor.setReadOnly(true);
    }

    switch (this.algorithm.language.toLowerCase()) {
      case 'javascript':
        this.editor.getSession().setMode('ace/mode/javascript');
        break;
      case 'python':
        this.editor.getSession().setMode('ace/mode/python');
        break;
      default:
        console.warn(`Unexpected language ${this.algorithm.language}`);
    }

    this.editor.setTheme('ace/theme/eclipse');
  }

  private refreshOverrides() {
    const qualifiedName = this.route.parent!.snapshot.paramMap.get('qualifiedName')!;
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient.getAlgorithmOverrides(instance, processor, qualifiedName).then(overrides => {
      this.overrides$.next(overrides);
      if (overrides.textOverride) {
        this.updateEditorValue(overrides.textOverride.text);
      } else {
        this.updateEditorValue(this.algorithm.text);
      }
    }).catch(err => this.messageService.showError(err));
  }

  private updateEditorValue(text: string) {
    this.editor.session.setValue(text);
    this.dirty$.next(false);
  }

  saveTextChanges() {
    const text = this.editor.getSession().getValue();
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient.updateAlgorithmText(instance, processor, this.algorithm.qualifiedName, text)
      .then(() => this.refreshOverrides())
      .catch(err => this.messageService.showError(err));
  }

  revertText() {
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient.revertAlgorithmText(instance, processor, this.algorithm.qualifiedName)
      .then(() => this.refreshOverrides())
      .catch(err => this.messageService.showError(err));
  }
}
