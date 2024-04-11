import { Pipe, PipeTransform } from '@angular/core';
import { ColumnData, Value } from '@yamcs/webapp-sdk';

@Pipe({
  standalone: true,
  name: 'columnValue',
})
export class ColumnValuePipe implements PipeTransform {

  transform(columnData: ColumnData[] | null | undefined, name: string): Value | null {
    if (!columnData || columnData.length === 0) {
      return null;
    }
    for (const item of columnData) {
      if (item.name === name) {
        return item.value;
      }
    }
    return null;
  }
}
