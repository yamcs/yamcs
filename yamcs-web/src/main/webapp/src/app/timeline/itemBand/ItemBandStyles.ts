import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { Option } from '../../shared/forms/Select';

export interface ItemBandProperties {
  [key: string]: any,
  itemBackgroundColor: string,
  itemBorderColor: string,
  itemBorderWidth: number,
  itemCornerRadius: number,
  itemHeight: number,
  itemMarginLeft: number,
  itemTextColor: string,
  itemTextOverflow: 'show' | 'clip' | 'hide',
  itemTextSize: number,
  marginBottom: number,
  marginTop: number,
  multiline: boolean,
  spaceBetweenItems: number,
  spaceBetweenLines: number,
}

export const defaultProperties: ItemBandProperties = {
  itemBackgroundColor: '#77b1e1',
  itemBorderColor: '#3d94c7',
  itemBorderWidth: 1,
  itemCornerRadius: 0,
  itemHeight: 20,
  itemMarginLeft: 5,
  itemTextColor: '#333333',
  itemTextOverflow: 'show',
  itemTextSize: 10,
  marginBottom: 7,
  marginTop: 7,
  multiline: true,
  spaceBetweenItems: 0,
  spaceBetweenLines: 2,
};

export function addDefaultItemBandProperties(partial: Partial<ItemBandProperties>) {
  return mergeProperties(defaultProperties, partial);
}

export function mergeProperties(base: ItemBandProperties, partial: Partial<ItemBandProperties>) {
  const result: ItemBandProperties = { ...base };
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
  selector: 'app-item-band-styles',
  templateUrl: './ItemBandStyles.html',
  styleUrls: ['../StyleTable.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemBandStyles {

  itemTextOverflowOptions: Option[] = [
    { id: 'show', label: 'Show' },
    { id: 'clip', label: 'Clip' },
    { id: 'hide', label: 'Hide' },
  ];

  @Input()
  form: FormGroup;
}
