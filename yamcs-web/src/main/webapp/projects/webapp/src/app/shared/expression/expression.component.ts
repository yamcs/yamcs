import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  SecurityContext,
  viewChild,
} from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { FormulaCompiler } from '@yamcs/opi';
import { utils, YamcsService } from '@yamcs/webapp-sdk';

function replaceAll(str: string, find: string, replace: string) {
  const escaped = find.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); // $& means the whole matched string
  return str.replace(new RegExp(escaped, 'g'), replace);
}

@Component({
  selector: 'app-expression',
  template: '<span #expr></span>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpressionComponent implements AfterViewInit {
  @Input()
  expression: string;

  @Input()
  relto?: string;

  ref = viewChild.required<ElementRef<HTMLSpanElement>>('expr');

  constructor(
    private sanitizer: DomSanitizer,
    private router: Router,
    private yamcs: YamcsService,
  ) {}

  ngAfterViewInit() {
    let html = this.expression;

    const compiler = new FormulaCompiler();
    const script = compiler.compile('=' + this.expression);

    const parameters = script.getPVNames();
    for (let i = 0; i < parameters.length; i++) {
      let qualifiedName: string;

      const isRaw = parameters[i].startsWith('raw://');
      if (isRaw) {
        qualifiedName = parameters[i].substring('raw://'.length);
      } else {
        qualifiedName = parameters[i];
      }

      let replacement = parameters[i];
      if (this.relto) {
        replacement = utils.relativizePath(qualifiedName, this.relto)!;
      }
      if (isRaw) {
        replacement = 'raw://' + replacement;
      }

      let url;
      if (this.router.url.startsWith('/mdb/')) {
        url = this.router
          .createUrlTree(['/mdb/parameters/', qualifiedName], {
            queryParams: {
              c: this.yamcs.context,
            },
          })
          .toString();
      } else {
        url = this.router
          .createUrlTree(['/telemetry/parameters' + qualifiedName], {
            queryParams: {
              c: this.yamcs.context,
            },
          })
          .toString();
      }

      replacement = `<a class="ya-link" href="${url}" title="${parameters[i]}">${replacement}</a>`;
      html = replaceAll(html, `'${parameters[i]}'`, replacement);
    }

    const sanitizedHtml = this.sanitizer.sanitize(SecurityContext.HTML, html);
    this.ref().nativeElement.innerHTML = sanitizedHtml || '';
  }
}
