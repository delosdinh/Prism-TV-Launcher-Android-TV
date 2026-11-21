# Prism Keyboard dictionary

`app/src/main/res/raw/en_words.txt` is the word list behind Prism Keyboard's suggestions and
auto-correction: the 56,000 most common English words, most frequent first. Words marked with `!` are
known words that are never offered as suggestions (`quiet.txt`).

Rebuild it with:

```
pip install wordfreq
cd tools/keyboard && python3 build_dictionary.py ../../app/src/main/res/raw/en_words.txt
```

Word frequencies come from [wordfreq](https://github.com/rspeer/wordfreq) by Robyn Speer, whose data
is licensed under CC BY-SA 4.0; the derived list is shared under the same licence.
