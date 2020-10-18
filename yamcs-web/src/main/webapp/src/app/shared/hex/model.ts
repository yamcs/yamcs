function lpad(hex: string, width: number) {
  if (hex.length >= width) {
    return hex;
  } else {
    return new Array(width - hex.length + 1).join('0') + hex;
  }
}

export class BitRange {

  constructor(readonly start: number, readonly bitlength: number) {
  }

  get stop() {
    return this.start + this.bitlength;
  }

  join(other: BitRange) {
    const start = Math.min(this.start, other.start);
    const stop = Math.max(this.stop, other.stop);
    return new BitRange(start, stop - start);
  }

  joinBit(bitpos: number) {
    const start = Math.min(this.start, bitpos);
    const stop = Math.max(this.stop, bitpos);
    return new BitRange(start, stop - start);
  }

  containsBit(bitpos: number) {
    return this.start <= bitpos && bitpos <= this.stop;
  }

  containsBitExclusive(bitpos: number) {
    return this.start < bitpos && bitpos < this.stop;
  }

  overlaps(other: BitRange) {
    return other.start >= this.start && other.stop <= this.stop;
  }

  intersect(other: BitRange): BitRange | null {
    if (other.start > this.stop || this.start > other.stop) {
      return null;
    }
    const start = Math.max(this.start, other.start);
    const stop = Math.min(this.stop, other.stop);
    return new BitRange(start, stop - start);
  }
}

export class HexModel {
  readonly bitlength: number;
  readonly lines: Line[] = [];

  constructor(private raw: string) {
    this.bitlength = raw.length * 8;
    for (let i = 0; i < raw.length; i += 16) {
      this.lines.push(new Line(i, raw.substring(i, i + 16)));
    }
  }

  printHex() {
    let result = '';
    for (let i = 0; i < this.raw.length; i++) {
      const hex = this.raw.charCodeAt(i).toString(16);
      result += (hex.length === 2 ? hex : '0' + hex);
    }
    return result;
  }
}

interface WordHex {
  type: 'word';
  bitpos: number;
  nibbles: string[];
}

interface Filler {
  type: 'filler';
  bitpos: number;
  content: string;
}

interface WordAscii {
  type: 'word';
  bitpos: number;
  chars: string[];
}

export class Line {
  bitpos: number;
  charCountHex: string;
  hexComponents: (WordHex | Filler)[] = [];
  asciiComponents: (WordAscii | Filler)[] = [];

  private wordCount = 0;

  constructor(charCount: number, chars: string) {
    this.bitpos = charCount * 8;
    this.charCountHex = lpad(charCount.toString(16), 4);
    for (let i = 0; i < chars.length; i += 2) {
      this.addWord(chars.substring(i, i + 2));
    }

    if (chars.length < 16) {
      let asciiFiller = ' '.repeat(16 - chars.length);
      this.asciiComponents.push({
        type: 'filler',
        content: asciiFiller,
        bitpos: this.bitpos + (chars.length * 8),
      });

      let hexFiller = '';
      for (let j = 0; j < 32 - (2 * (chars.length % 16)); j++) {
        if (j !== 0 && ((2 * chars.length) + j) % 4 === 0) {
          hexFiller += '  ';
        } else {
          hexFiller += ' ';
        }
      }

      hexFiller += ' ';
      this.hexComponents.push({
        type: 'filler',
        content: hexFiller,
        bitpos: this.bitpos + (chars.length * 8),
      });
    }
  }

  /*
   * Adds a word (two characters, four nibbles, 16 bits)
   */
  private addWord(word: string) {
    let hex = '';
    let ascii = '';

    for (let i = 0; i < word.length; i++) {
      hex += this.charToHex(word[i]);
      ascii += this.charToAscii(word[i]);
    }

    const bitpos = this.bitpos + (this.wordCount * 16);
    this.hexComponents.push({ type: 'word', nibbles: [...hex], bitpos });
    if (word.length === 2) {
      this.hexComponents.push({ type: 'filler', content: ' ', bitpos: bitpos + 16 });
    }

    this.asciiComponents.push({ type: 'word', chars: [...ascii], bitpos });
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
