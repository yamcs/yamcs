import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { Router } from '@angular/router';
import { Parameter, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { debounceTime, map, Observable, of, switchMap } from 'rxjs';

@Component({
  selector: 'app-search-input',
  templateUrl: './search-input.component.html',
  styleUrl: './search-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
  host: {
    '[class.expanded]': 'expanded()',
    '(document:keydown)': 'handleDocumentKeyDown($event)',
    '(keydown)': 'handleComponentKeyDown($event)',
  },
})
export class SearchInputComponent implements OnInit {
  expanded = signal(false);

  @ViewChild('searchInput')
  searchInput: ElementRef<HTMLInputElement>;

  searchControl = new UntypedFormControl(null);
  filteredOptions: Observable<Parameter[]>;

  constructor(
    readonly yamcs: YamcsService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.filteredOptions = this.searchControl.valueChanges.pipe(
      debounceTime(300),
      switchMap((val) => {
        if (val) {
          return this.yamcs.yamcsClient.getParameters(this.yamcs.instance!, {
            q: val,
            limit: 25,
            searchMembers: true,
          });
        } else {
          return of({ parameters: [] });
        }
      }),
      map((page) => page.parameters || []),
    );
  }

  toggle() {
    if (this.expanded()) {
      this.collapse();
    } else {
      this.expand();
    }
  }

  collapse() {
    this.expanded.set(false);
  }

  expand() {
    this.expanded.set(true);
    setTimeout(() => {
      this.searchInput.nativeElement.focus();
    });
  }

  onSearchSelect(event: MatAutocompleteSelectedEvent) {
    this.searchControl.setValue('');
    this.router.navigate(['/telemetry/parameters' + event.option.value], {
      queryParams: { c: this.yamcs.context },
    });
  }

  handleDocumentKeyDown(event: KeyboardEvent) {
    if (event.key === '/' && this.isValidKeySource()) {
      this.expand();
      event.preventDefault();
    } else if (event.key === 'Enter') {
      const value = this.searchControl.value;
      if (value) {
        this.searchControl.setValue('');
        this.router.navigate(['/search'], {
          queryParams: { c: this.yamcs.context, q: value },
        });
      }
    }
  }

  private isValidKeySource() {
    const { activeElement } = document;
    if (!activeElement) {
      return true;
    }
    return (
      activeElement.tagName !== 'INPUT' &&
      activeElement.tagName !== 'SELECT' &&
      activeElement.tagName !== 'TEXTAREA' &&
      // Exclude CodeMirror editor
      !activeElement.classList.contains('cm-content')
    );
  }

  handleComponentKeyDown(event: KeyboardEvent) {
    event.stopPropagation();
  }
}
