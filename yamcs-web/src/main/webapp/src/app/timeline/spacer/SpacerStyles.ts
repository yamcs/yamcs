import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';

export interface SpacerProperties {
  [key: string]: any,
  height: number;
}

export const defaultProperties: SpacerProperties = {
  height: 34,
};

export function addDefaultSpacerProperties(partial: Partial<SpacerProperties>) {
  return mergeProperties(defaultProperties, partial);
}

export function mergeProperties(base: SpacerProperties, partial: Partial<SpacerProperties>) {
  const result: SpacerProperties = { ...base };
  // Use defaultProperties as reference because it includes correct typing
  // (value from server is always strings)
  for (const property in defaultProperties) {
    const value = base[property];
    if (partial[property]) {
      if (typeof value === 'boolean') {
        result[property] = partial[property] === 'true';
      } else if (typeof value === 'number') {
        result[property] = Number(partial[property]);
      } else {
        result[property] = partial[property];
      }
    }
  }
  return result;
}

@Component({
  selector: 'app-spacer-styles',
  templateUrl: './SpacerStyles.html',
  styleUrls: ['../StyleTable.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpacerStyles {

  @Input()
  form: UntypedFormGroup;
}
