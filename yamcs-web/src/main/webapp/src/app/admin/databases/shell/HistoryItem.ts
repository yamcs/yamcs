export class HistoryItem {

  private edited: string;

  constructor(private text = '') {
    this.edited = text;
  }

  backspace(cursor: number) {
    if (cursor > 0) {
      const chars = this.edited.split('');
      chars.splice(cursor - 1, 1);
      this.edited = chars.join('');
    }
  }

  insert(cursor: number, value: string) {
    const chars = this.edited.split('');
    chars.splice(cursor, 0, value);
    this.edited = chars.join('');
  }

  get value(): string {
    return this.edited;
  }

  commit() {
    this.text = this.edited;
  }

  revert() {
    this.edited = this.text;
  }
}
