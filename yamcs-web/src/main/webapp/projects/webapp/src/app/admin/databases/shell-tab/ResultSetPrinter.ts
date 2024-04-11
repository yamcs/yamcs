import { utils } from '@yamcs/webapp-sdk';

export class ResultSetPrinter {

  widths: number[] = [];
  pendingRows: Array<string[]> = [];
  printedRowCount = 0;

  constructor(private columns: any) {
    for (const column of columns) {
      this.widths.push(column.name.length);
    }
  }

  add(row: any) {
    const printedRow: string[] = [];
    for (let i = 0; i < row.values.length; i++) {
      const stringValue = utils.printValue(row.values[i]) || '';
      printedRow.push(stringValue);
      this.widths[i] = Math.max(stringValue.length, this.widths[i]);
    }
    this.pendingRows.push(printedRow);
  }

  printPending(): string[] {
    const result: string[] = [];
    const columnNames = this.columns.map((c: any) => c.name);
    if (this.printedRowCount === 0) {
      result.push(this.generateSeparator());
      result.push(this.printRow(columnNames));
      result.push(this.generateSeparator());
    }

    for (const row of this.pendingRows) {
      result.push(this.printRow(row));
      this.printedRowCount++;
    }

    return result;
  }

  private printRow(values: string[]) {
    let line = '';
    for (let i = 0; i < this.columns.length; i++) {
      line += '| ';
      line += values[i];
      line += ' '.repeat(this.widths[i] - values[i].length);
      line += ' ';
    }
    return line + '|';
  }

  printSummary(): string[] {
    const result: string[] = [];
    result.push(this.generateSeparator());
    if (this.printedRowCount === 1) {
      result.push('1 row in set');
    } else {
      result.push(`${this.printedRowCount} rows in set`);
    }
    result.push('');
    return result;
  }

  private generateSeparator() {
    let separator = '';
    for (const width of this.widths) {
      separator += "+-" + "-".repeat(width) + "-";
    }
    separator += "+";
    return separator;
  }
}
