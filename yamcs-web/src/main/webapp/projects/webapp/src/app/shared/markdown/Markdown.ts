import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, OnChanges, SecurityContext, ViewChild } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { marked } from 'marked';

@Component({
  selector: 'app-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './Markdown.html',
})
export class Markdown implements AfterViewInit, OnChanges {

  @Input()
  text: string;

  @ViewChild('md')
  ref: ElementRef<HTMLDivElement>;

  constructor(private sanitizer: DomSanitizer) {
  }

  ngAfterViewInit() {
    this.updateContent();
  }

  ngOnChanges() {
    if (this.ref) {
      this.updateContent();
    }
  }

  private updateContent() {
    let html = this.text ? marked.parse(this.text) : '';
    html = this.sanitizer.sanitize(SecurityContext.HTML, html)!;
    this.ref.nativeElement.innerHTML = html;
  }
}
