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

## Next-word predictions

`app/src/main/res/raw/en_next.txt` holds the words most likely to follow a word (or two), for the
suggestion bar after a space: one context per line, `context<TAB>next next…`, most likely first. Rebuild
it (it downloads the lists on first run, into `.cache/`):

```
cd tools/keyboard && python3 build_next_words.py ../../app/src/main/res/raw/en_next.txt
```

It comes from the most frequent English 2-grams and 3-grams (general and fiction, books from 2010–2019)
of the [Google Books Ngram Corpus](https://books.google.com/ngrams/), as cleaned and published by
[orgtre/google-books-ngram-frequency](https://github.com/orgtre/google-books-ngram-frequency), licensed
under [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). Only words in `en_words.txt` are kept.
Prism Keyboard also learns the phrases you type (kept on the TV, at most 1,500) and offers those first.
