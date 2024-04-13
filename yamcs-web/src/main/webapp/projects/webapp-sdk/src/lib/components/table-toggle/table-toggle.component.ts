import { ChangeDetectionStrategy, Component, Input, OnInit, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { PreferenceStore } from '../../services/preference-store.service';


@Component({
  standalone: true,
  selector: 'ya-table-toggle',
  templateUrl: './table-toggle.component.html',
  styleUrl: './table-toggle.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatSlideToggle,
    ReactiveFormsModule,
  ],
})
export class YaTableToggle implements OnInit {

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
