import { Component } from '@angular/core';

@Component({
  selector: 'ya-attr-list',
  template: '<ng-content />',
  styleUrl: './attr-list.component.css',
  host: {
    class: 'ya-attr-list',
  },
})
export class YaAttrList {}
