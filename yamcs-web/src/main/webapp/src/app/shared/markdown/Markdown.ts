import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, OnChanges, ViewChild } from '@angular/core';
import * as showdown from 'showdown';

@Component({
  selector: 'app-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './Markdown.html',
})
export class Markdown implements AfterViewInit, OnChanges {

  @Input()
  text: string;

  @ViewChild('md')
  ref: ElementRef;

  ngAfterViewInit() {
    const html = this.text ? new showdown.Converter().makeHtml(this.text) : '';
    this.ref.nativeElement.innerHTML = html;
  }

  ngOnChanges() {
    if (this.ref) {
      const html = this.text ? new showdown.Converter().makeHtml(this.text) : '';
      this.ref.nativeElement.innerHTML = html;
    }
  }
}
