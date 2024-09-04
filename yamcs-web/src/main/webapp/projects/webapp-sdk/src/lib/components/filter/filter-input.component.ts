import { AfterViewInit, ChangeDetectionStrategy, Component, effect, ElementRef, forwardRef, input, OnDestroy, output, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';
import { Completion } from '@codemirror/autocomplete';
import { EditorState, StateEffect, StateEffectType, StateField } from '@codemirror/state';
import { Decoration, DecorationSet } from "@codemirror/view";
import { EditorView } from 'codemirror';
import { provideCodeMirrorSetup } from './cmSetup';
import { FilterErrorMark } from './FilterErrorMark';
import { filter } from './lang-filter';

@Component({
  standalone: true,
  selector: 'ya-filter-input',
  templateUrl: './filter-input.component.html',
  styleUrl: './filter-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => YaFilterInput),
    multi: true,
  }],
  imports: [
    MatIcon,
  ],
})
export class YaFilterInput implements ControlValueAccessor, AfterViewInit, OnDestroy {

  completions = input<Completion[]>();
  errorMark = input<FilterErrorMark>();
  placeholder = input<string>();
  icon = input<string>();

  onEnter = output<string>();

  private editorContainerRef = viewChild.required<ElementRef<HTMLDivElement>>('editorContainer');
  private editorView: EditorView | null = null;
  private underlineDecoration: Decoration;
  private addUnderlineEffect: StateEffectType<{ from: number; to: number; }>;
  private removeUnderlineEffect: StateEffectType<null>;

  showClear = signal<boolean>(false);

  private onChange = (_: string | null) => { };

  // Internal value, for when a value is received before CM init
  private initialDocString: string | undefined;

  constructor() {
    effect(() => {
      const errorMark = this.errorMark();

      // Remove any old effect, before adding a new one
      this.editorView!.dispatch({
        effects: this.removeUnderlineEffect.of(null),
      });

      if (errorMark) {
        const { doc } = this.editorView!.state;
        const { beginLine, beginColumn, endLine, endColumn } = errorMark;

        const beginOffset = doc.line(beginLine).from + (beginColumn - 1);
        const endOffset = doc.line(endLine).from + endColumn;
        this.editorView!.dispatch({
          effects: this.addUnderlineEffect.of(this.underlineDecoration.range(beginOffset, endOffset)),
        });
      }
    });
  }

  writeValue(value: any): void {
    this.initialDocString = value || undefined;
    this.editorView?.dispatch({
      changes: {
        from: 0,
        to: this.editorView.state.doc.length,
        insert: this.initialDocString,
      },
    });
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngAfterViewInit(): void {
    const targetEl = this.editorContainerRef().nativeElement;
    this.initializeEditor(targetEl);
  }

  focus() {
    this.editorView?.focus();
  }

  clearInput() {
    this.writeValue(null);
    this.focus();
  }

  private initializeEditor(targetEl: HTMLDivElement) {
    this.addUnderlineEffect = StateEffect.define({
      map: ({ from, to }, change) => ({ from: change.mapPos(from), to: change.mapPos(to) })
    });
    this.removeUnderlineEffect = StateEffect.define();

    this.underlineDecoration = Decoration.mark({ class: 'cm-underline' });

    const that = this;
    const underlineExtension = StateField.define<DecorationSet>({
      create() {
        return Decoration.none;
      },
      update(value, transaction) {

        // Move the decorations to account for document changes
        value = value.map(transaction.changes);

        for (const effect of transaction.effects) {
          if (effect.is(that.addUnderlineEffect)) {
            value = value.update({
              add: [that.underlineDecoration.range(effect.value.from, effect.value.to)],
            });
          } else if (effect.is(that.removeUnderlineEffect)) {
            value = value.update({
              filter: (f, t, value) => false,
            });
          }
        }
        return value;
      },
      provide: f => EditorView.decorations.from(f)
    });

    const state = EditorState.create({
      doc: this.initialDocString,
      extensions: [
        provideCodeMirrorSetup({
          oneline: true,
          placeholder: this.placeholder(),
          paddingLeft: this.icon() ? '24px' : undefined,
          onEnter: view => {
            this.onEnter.emit(view.state.doc.toString());
          },
          completions: this.completions(),
        }),
        filter(),
        EditorView.updateListener.of(update => {
          if (update.docChanged) {
            const newValue = update.state.doc.toString();
            this.onChange(newValue);
            this.showClear.set(!!newValue);
          }
        }),
        underlineExtension,
      ],
    });

    this.editorView = new EditorView({
      state,
      parent: targetEl,
    });
    this.showClear.set(!!this.initialDocString);
  }

  ngOnDestroy(): void {
    this.editorView?.destroy();
    this.editorView = null;
  }
}
