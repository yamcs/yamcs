import { Font, FontResolver } from '@yamcs/opi';

export class OpiDisplayFontResolver implements FontResolver {

  constructor(private prefix: string) {
  }

  resolve(font: Font): FontFace | undefined {
    let file;
    if (font.name === "Liberation Sans") {
      file = "LiberationSans-Regular.woff2";
      if (font.bold && font.italic) {
        file = "LiberationSans-BoldItalic.woff2";
      } else if (font.bold) {
        file = "LiberationSans-Bold.woff2";
      } else if (font.italic) {
        file = "LiberationSans-Italic.woff2";
      }
    } else if (font.name === "Liberation Mono") {
      file = "LiberationMono-Regular.woff2";
      if (font.bold && font.italic) {
        file = "LiberationMono-BoldItalic.woff2";
      } else if (font.bold) {
        file = "LiberationMono-Bold.woff2";
      } else if (font.italic) {
        file = "LiberationMono-Italic.woff2";
      }
    } else if (font.name === "Liberation Serif") {
      file = "LiberationSerif-Regular.woff2";
      if (font.bold && font.italic) {
        file = "LiberationSerif-BoldItalic.woff2";
      } else if (font.bold) {
        file = "LiberationSerif-Bold.woff2";
      } else if (font.italic) {
        file = "LiberationSerif-Italic.woff2";
      }
    }

    if (file) {
      return new FontFace(font.name, `url(${this.prefix}media/${file})`, {
        weight: font.bold ? "bold" : "normal",
        style: font.italic ? "italic" : "normal",
      });
    }
  }
}
