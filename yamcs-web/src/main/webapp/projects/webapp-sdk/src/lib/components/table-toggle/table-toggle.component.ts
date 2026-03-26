import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnInit,
  inject,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Preferences } from '../../services/preferences.service';
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

  private prefs = inject(Preferences);

  ngOnInit() {
    if (this.preferenceKey) {
      const checked = this.prefs.getBoolean(this.preferenceKey, false);
      this.formControl.setValue(checked);
      this.formControl.valueChanges.subscribe((checked) => {
        this.prefs.setBoolean(this.preferenceKey, checked);
      });
    }
  }

  get checked() {
    return this.formControl.value ?? false;
  }
}
