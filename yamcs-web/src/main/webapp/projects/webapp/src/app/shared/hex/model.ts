import { BitRange } from '@yamcs/webapp-sdk';

let seq = 0;

export class HexModel {
  readonly bitlength: number;

  readonly lines: Line[] = [];

  constructor(raw: string) {
    this.bitlength = raw.length * 8;
    for (let i = 0; i < raw.length; i += 16) {
      this.lines.push(new Line(this, i, raw.substring(i, i + 16)));
    }
  }
}

interface WordHex {
  id: string;
  type: 'word';
  range: BitRange;
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
  id: string;
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

  wordCount = 0;

  constructor(readonly model: HexModel, charCount: number, readonly chars: string) {
    this.id = 'p' + seq++;
    this.range = new BitRange(charCount * 8, chars.length * 8);
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
    }
  }

  get hexLengthInChars(): number {
    let result = 0;
    for (const component of this.hexComponents) {
      if (component.type === 'word') {
        result += component.nibbles.length;
      } else if (component.type === 'filler' && !component.trailing) {
        result += component.content.length;
      }
    }
    return result;
  }

  get asciiLengthInChars(): number {
    let result = 0;
    for (const component of this.asciiComponents) {
      if (component.type === 'word') {
        result += component.chars.length;
      } else if (component.type === 'filler' && !component.trailing) {
        result += component.content.length;
      }
    }
    return result;
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
    }
    this.hexComponents.push({
      id: 'p' + seq++,
      type: 'word',
      nibbles,
      range: new BitRange(bitpos, 4 * nibbles.length),
    });
    if (word.length === 2) {
      const filler: Filler = {
        id: 'p' + seq++,
        type: 'filler',
        content: ' ',
        bitpos: bitpos + 16,
        trailing: last,
      };
      this.hexComponents.push(filler);
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
    }
    this.asciiComponents.push({
      id: 'p' + seq++,
      type: 'word',
      chars,
      bitpos,
    });
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
