import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnInit,
  inject,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { PreferenceStore } from '../../services/preference-store.service';
import { YaSlideToggle } from '../slide-toggle/slide-toggle.component';

@Component({
  selector: 'ya-table-toggle',
  templateUrl: './table-toggle.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, YaSlideToggle],
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
      this.formControl.valueChanges.subscribe((checked) => {
        this.preferenceStore.setValue(this.preferenceKey, checked);
      });
    }
  }

  get checked() {
    return this.formControl.value ?? false;
  }
}
