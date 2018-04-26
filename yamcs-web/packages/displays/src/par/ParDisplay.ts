import { Svg, Rect, Tag, Defs, Pattern } from '../tags';
import { DisplayCommunicator } from '../DisplayCommunicator';
import { DisplayFrame } from '../DisplayFrame';
import { ParameterValue, NamedObjectId } from '@yamcs/client';
import { Display } from '../Display';

export class ParDisplay implements Display {

  title: string;
  width: number;
  height: number;

  constructor(
    readonly frame: DisplayFrame,
    private targetEl: HTMLDivElement,
    readonly displayCommunicator: DisplayCommunicator,
  ) {}

  parseAndDraw(id: string, grid = false) {
    return this.displayCommunicator.retrieveDisplayResource(id).then(json => {
      const obj = JSON.parse(json);
      // console.log('got back', obj);

      this.title = 'Parameter Table2';
      this.width = 300;
      this.height = 300;

      const rootEl = document.createElement('div');
      rootEl.setAttribute('style', 'x: 0; y: 0; width: 300px; height: 300px');

      const tableEl = document.createElement('table');
      const tbodyEl = document.createElement('tbody');
      for (const qualifiedName of obj.parameters) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.style.lineHeight = '12px';
        td.appendChild(document.createTextNode(qualifiedName));
        tr.appendChild(td);
        tbodyEl.appendChild(tr);
      }
      tableEl.appendChild(tbodyEl);
      rootEl.appendChild(tableEl);
      this.targetEl.appendChild(rootEl);
    });
  }

  public getBackgroundColor() {
    return 'red';
  }

  getParameterIds() {
    const ids: NamedObjectId[] = [];
    return ids;
  }

  getDataSourceState() {
    const green = false;
    const yellow = false;
    const red = false;
    return { green, yellow, red };
  }

  processParameterValues(pvals: ParameterValue[]) {
  }

  digest() {
  }
}
