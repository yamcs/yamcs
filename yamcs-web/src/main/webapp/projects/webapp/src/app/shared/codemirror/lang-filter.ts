import { LanguageSupport, LRLanguage } from "@codemirror/language";
import { parser } from "./parser";

export const filterLanguage = LRLanguage.define({
  name: "filter",
  parser: parser.configure({}),
  languageData: {
    closeBrackets: { brackets: ["(", '"'] },
  },
});

export function filter() {
  return new LanguageSupport(filterLanguage);
}
