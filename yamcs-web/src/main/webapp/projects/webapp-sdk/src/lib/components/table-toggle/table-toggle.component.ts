import { ChangeDetectionStrategy, Component, Input, OnInit, inject } from '@angular/core';
import { FormControl } from '@angular/forms';
import { PreferenceStore } from '../../services/preference-store.service';


@Component({
  selector: 'ya-table-toggle',
  templateUrl: './table-toggle.component.html',
  styleUrls: ['./table-toggle.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableToggleComponent implements OnInit {

  @Input()
  preferenceKey: string;

  formControl = new FormControl<boolean>(false);

  private preferenceStore = inject(PreferenceStore);

  ngOnInit() {
    if (this.preferenceKey) {
      this.preferenceStore.addPreference$(this.preferenceKey, false);
      const checked = this.preferenceStore.getValue(this.preferenceKey);
      this.formControl.setValue(checked);
      this.formControl.valueChanges.subscribe(checked => {
        this.preferenceStore.setValue(this.preferenceKey, checked);
      });
    }
  }

  get checked() {
    return this.formControl.value ?? false;
  }
}
