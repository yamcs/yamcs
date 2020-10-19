import { BitRange } from '../BitRange';


let seq = 0;

export class HexModel {
  readonly bitlength: number;

  readonly lines: Line[] = [];

  readonly positionables = new Map<string, number>();

  constructor(raw: string) {
    this.bitlength = raw.length * 8;
    for (let i = 0; i < raw.length; i += 16) {
      this.lines.push(new Line(this, i, raw.substring(i, i + 16)));
    }
  }

  private nextId(bitpos: number) {
    const lineId = 'p' + seq++;
    this.positionables.set(lineId, bitpos);
    return lineId;
  }

  printHTML() {
    let result = '';
    for (const line of this.lines) {
      result += `<div class="line" id="${line.id}">`;
      result += `<span class="cnt">${line.charCountHex}: </span>`;
      result += '<span class="hex">';
      for (let i = 0; i < line.hexComponents.length; i++) {
        const component = line.hexComponents[i];
        if (component.type === 'word') {
          result += `<span class="word" id="${this.nextId(component.bitpos)}">`;
          for (const nibble of component.nibbles) {
            result += `<span class="nibble" id="${nibble.id}">${nibble.content}</span>`;
          }
          result += '</span>';
        } else if (component.type === 'filler') {
          const classNames = (i === line.hexComponents.length - 1) ? 'filler last' : 'filler';
          result += `<span class="${classNames}" id="${component.id}">${component.content}</span>`;
        }
      }
      result += '</span>';
      result += '<span class="ascii">';
      for (let i = 0; i < line.asciiComponents.length; i++) {
        const component = line.asciiComponents[i];
        if (component.type === 'word') {
          result += `<span class="word" id="${this.nextId(component.bitpos)}">`;
          for (const c of component.chars) {
            result += `<span class="char" id="${c.id}">${c.content}</span>`;
          }
          result += '</span>';
        } else if (component.type === 'filler') {
          const classNames = (i === line.asciiComponents.length - 1) ? 'filler last' : 'filler';
          result += `<span class="${classNames}" id="${component.id}">${component.content}</span>`;
        }
      }
      result += '</span>';
      result += '</div>';
    }
    return result;
  }
}

interface WordHex {
  type: 'word';
  bitpos: number;
  nibbles: NibbleHex[];
}

interface NibbleHex {
  id: string;
  range: BitRange;
  content: string;
}

interface Filler {
  id: string;
  type: 'filler';
  bitpos: number;
  content: string;
  trailing: boolean;
}

interface WordAscii {
  type: 'word';
  bitpos: number;
  chars: CharAscii[];
}

interface CharAscii {
  id: string;
  range: BitRange;
  content: string;
}

export class Line {
  id: string;
  range: BitRange;
  charCountHex: string;
  hexComponents: (WordHex | Filler)[] = [];
  asciiComponents: (WordAscii | Filler)[] = [];

  private wordCount = 0;

  constructor(readonly model: HexModel, charCount: number, chars: string) {
    this.id = 'p' + seq++;
    this.range = new BitRange(charCount * 8, chars.length * 8);
    this.model.positionables.set(this.id, this.range.start);
    this.charCountHex = this.lpad(charCount.toString(16), 4);
    for (let i = 0; i < chars.length; i += 2) {
      const last = (i + 2 >= chars.length);
      this.addWord(chars.substring(i, i + 2), last);
    }

    if (chars.length < 16) {
      let filler: Filler = {
        id: 'p' + seq++,
        type: 'filler',
        content: ' '.repeat(16 - chars.length),
        bitpos: this.range.stop,
        trailing: true,
      };
      this.asciiComponents.push(filler);
      this.model.positionables.set(filler.id, filler.bitpos);

      let hexFiller = '';
      for (let j = 0; j < 32 - (2 * (chars.length % 16)); j++) {
        if (j !== 0 && ((2 * chars.length) + j) % 4 === 0) {
          hexFiller += '  ';
        } else {
          hexFiller += ' ';
        }
      }

      hexFiller += ' ';
      filler = {
        id: 'p' + seq++,
        type: 'filler',
        content: hexFiller,
        bitpos: this.range.stop,
        trailing: true,
      };
      this.hexComponents.push(filler);
      this.model.positionables.set(filler.id, filler.bitpos);
    }
  }

  private lpad(hex: string, width: number) {
    if (hex.length >= width) {
      return hex;
    } else {
      return new Array(width - hex.length + 1).join('0') + hex;
    }
  }

  /*
   * Adds a word (two characters, four nibbles, 16 bits)
   */
  private addWord(word: string, last: boolean) {
    let hex = '';
    let ascii = '';

    for (let i = 0; i < word.length; i++) {
      hex += this.charToHex(word[i]);
      ascii += this.charToAscii(word[i]);
    }

    const bitpos = this.range.start + (this.wordCount * 16);
    const hexChars = hex.split('');
    const nibbles: NibbleHex[] = [];
    for (let i = 0; i < hexChars.length; i++) {
      const nibble = {
        id: 'p' + seq++,
        range: new BitRange(bitpos + (i * 4), 4),
        content: hexChars[i],
      };
      nibbles.push(nibble);
      this.model.positionables.set(nibble.id, nibble.range.start);
    }
    this.hexComponents.push({ type: 'word', nibbles, bitpos });
    if (word.length === 2) {
      const filler: Filler = {
        id: 'p' + seq++,
        type: 'filler',
        content: ' ',
        bitpos: bitpos + 16,
        trailing: last,
      };
      this.hexComponents.push(filler);
      this.model.positionables.set(filler.id, filler.bitpos);
    }

    const asciiChars = ascii.split('');
    const chars: CharAscii[] = [];
    for (let i = 0; i < asciiChars.length; i++) {
      const c = {
        id: 'p' + seq++,
        range: new BitRange(bitpos + (i * 8), 8),
        content: asciiChars[i],
      };
      chars.push(c);
      this.model.positionables.set(c.id, c.range.start);
    }
    this.asciiComponents.push({ type: 'word', chars, bitpos });
    this.wordCount++;
  }

  private charToHex(char: string) {
    const code = char.charCodeAt(0);
    const hex = code.toString(16);
    return (hex.length === 2) ? hex : '0' + hex;
  }

  private charToAscii(char: string) {
    const code = char.charCodeAt(0);
    return (32 <= code && code <= 126) ? char : '.';
  }
}
