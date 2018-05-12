import { Svg, Rect, Tag, Defs, Pattern } from '../tags';
import { DisplayCommunicator } from '../DisplayCommunicator';
import { DisplayFrame } from '../DisplayFrame';
import { ParameterValue, NamedObjectId, Value } from '@yamcs/client';
import { Display } from '../Display';

export class ParDisplay implements Display {

  title: string;
  width: number;
  height: number;

  // Element is an array because we don't explictly forbid a qualified name
  // to appear only once.
  rowElementsByQualifiedName = new Map<string, HTMLTableRowElement[]>();

  constructor(
    readonly frame: DisplayFrame,
    private targetEl: HTMLDivElement,
    readonly displayCommunicator: DisplayCommunicator,
  ) {}

  parseAndDraw(id: string, grid = false) {
    return this.displayCommunicator.retrieveDisplayResource(id).then(json => {
      const obj = JSON.parse(json);
      // console.log('got back', obj);

      this.title = 'Parameter Table';
      this.width = 300;
      this.height = 300;

      const rootEl = document.createElement('div');
      rootEl.setAttribute('style', 'x: 0; y: 0; width: 300px; height: 300px');

      const tableEl = document.createElement('table');
      const tbodyEl = document.createElement('tbody');
      for (const qualifiedName of obj.parameters) {
        const tr = document.createElement('tr');

        const nameCell = document.createElement('td');
        nameCell.style.lineHeight = '12px';
        nameCell.appendChild(document.createTextNode(qualifiedName));
        tr.appendChild(nameCell);

        const valueCell = document.createElement('td');
        valueCell.style.lineHeight = '12px';
        valueCell.appendChild(document.createTextNode('value'));
        tr.appendChild(valueCell);

        tbodyEl.appendChild(tr);

        const elements = this.rowElementsByQualifiedName.get(qualifiedName);
        if (elements) {
          elements.push(tr);
        } else {
          this.rowElementsByQualifiedName.set(qualifiedName, [tr]);
        }
      }
      tableEl.appendChild(tbodyEl);
      rootEl.appendChild(tableEl);
      this.targetEl.appendChild(rootEl);
    });
  }

  public getBackgroundColor() {
    return 'white';
  }

  getParameterIds() {
    const ids: NamedObjectId[] = [
      { name: '/YSS/SIMULATOR/Alpha' },
      { name: '/YSS/SIMULATOR/Beta' },
    ];
    return ids;
  }

  getDataSourceState() {
    const green = false;
    const yellow = false;
    const red = false;
    return { green, yellow, red };
  }

  processParameterValues(pvals: ParameterValue[]) {
    for (const pval of pvals) {
      const rows = this.rowElementsByQualifiedName.get(pval.id.name);
      if (rows) {
        for (const tr of rows) {
          tr.cells[1].innerHTML = this.valueToString(pval.engValue);
        }
      }
    }
  }

  digest() {
  }

  private valueToString(value: Value) {
    if (!value) {
      return '';
    }
    switch (value.type) {
      case 'FLOAT':
        return '' + value.floatValue;
      case 'DOUBLE':
        return '' + value.doubleValue;
      case 'UINT32':
        return '' + value.uint32Value;
      case 'SINT32':
        return '' + value.sint32Value;
      case 'BINARY':
        return '<binary>';
      case 'STRING':
        return value.stringValue!;
      case 'TIMESTAMP':
        const stringValue = value.stringValue!;
        return stringValue.replace('T', ' ').replace('Z', '');
      case 'UINT64':
        return '' + value.uint64Value;
      case 'SINT64':
        return '' + value.sint64Value;
      case 'BOOLEAN':
        return '' + value.booleanValue;
      default:
        return 'Unsupported data type';
    }
  }
}
