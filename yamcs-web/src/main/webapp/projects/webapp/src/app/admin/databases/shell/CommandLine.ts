import { HistoryItem } from './HistoryItem';
import { ShellComponent } from './shell.component';

export class CommandLine {

  private history: HistoryItem[] = [];
  private pointer = -1;

  private lastItem = new HistoryItem();

  cursor = 0;

  constructor(private shell: ShellComponent) {
    this.history.push(this.lastItem);
    this.pointer = 0;
  }

  consume(key: string) {
    switch (key) {
      case 'Backspace':
        this.history[this.pointer].backspace(this.cursor);
        this.cursor = Math.max(0, this.cursor - 1);
        break;
      case 'ArrowUp':
        this.pointer = Math.max(0, this.pointer - 1);
        this.cursor = this.history[this.pointer].value.length;
        break;
      case 'ArrowDown':
        this.pointer = Math.min(this.history.length - 1, this.pointer + 1);
        this.cursor = this.history[this.pointer].value.length;
        break;
      case 'ArrowLeft':
        this.cursor = Math.max(0, this.cursor - 1);
        break;
      case 'ArrowRight':
        const item = this.history[this.pointer];
        this.cursor = Math.min(item.value.length, this.cursor + 1);
        break;
      default:
        this.history[this.pointer].insert(this.cursor, key);
        this.cursor += key.length;
    }

    this.notifyChange();
  }

  private notifyChange() {
    this.shell.repaintCommandLine();
  }

  abort() {
    const input = this.history[this.pointer].value;

    for (const item of this.history) {
      item.revert();
    }
    this.cursor = 0;
    this.pointer = this.history.length - 1;
    this.notifyChange();

    return input;
  }

  peek() {
    return this.history[this.pointer].value;
  }

  take() {
    const input = this.history[this.pointer].value;

    // Remember edits to past lines, except for
    // the last line or the line that is to be
    // handled(becomes a new history item).
    for (let i = 0; i < this.history.length; i++) {
      if (i === this.pointer || i === this.history.length - 1) {
        this.history[i].revert();
      } else {
        this.history[i].commit();
      }
    }

    if (input) {
      const newItem = new HistoryItem(input);
      this.history.splice(this.history.length - 1, 0, newItem);
      this.pointer = this.history.length - 1;
    }

    this.cursor = 0;
    this.notifyChange();
    return input;
  }

  moveCursorToHome() {
    this.cursor = 0;
    this.notifyChange();
  }

  moveCursorToEnd() {
    this.cursor = this.history[this.pointer].value.length;
    this.notifyChange();
  }
}
