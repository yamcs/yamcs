import { Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

interface Entry {
  key: string;
  value: string;
}

@Component({
  selector: 'app-labels',
  templateUrl: './Labels.html',
  styleUrls: ['./Labels.css'],
})
export class Labels implements OnInit {

  @Input()
  dict: { [key: string]: string };

  sortedLabels$ = new BehaviorSubject<Entry[]>([]);

  ngOnInit() {
    const entries: Entry[] = [];
    if (this.dict) {
      for (const k in this.dict) {
        if (this.dict.hasOwnProperty(k)) {
          entries.push({
            key: k,
            value: this.dict[k],
          });
        }
      }
    }
    entries.sort((a, b) => a.key.localeCompare(b.key));
    this.sortedLabels$.next(entries);
  }
}
