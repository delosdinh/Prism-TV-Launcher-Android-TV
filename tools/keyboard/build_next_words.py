"""Builds app/src/main/res/raw/en_next.txt, Prism Keyboard's next-word predictions.

Source: the most frequent English 2-grams and 3-grams of the Google Books Ngram Corpus (v3, books
from 2010-2019), general English and English Fiction, as cleaned by
https://github.com/orgtre/google-books-ngram-frequency (CC BY 3.0).

Output, one context per line: `context<TAB>next next next…`, the context lowercase (one or two words),
the next words most likely first and in their usual case ("York" after "new", "I" stays a capital).
Only words in Prism's dictionary (en_words.txt) are kept, and never its quiet ones.

    cd tools/keyboard && python3 build_next_words.py ../../app/src/main/res/raw/en_next.txt
"""
import collections
import csv
import io
import os
import re
import sys
import urllib.request

BASE = "https://raw.githubusercontent.com/orgtre/google-books-ngram-frequency/main/ngrams/"
FILES = ["2grams_english.csv", "2grams_english-fiction.csv", "3grams_english.csv", "3grams_english-fiction.csv"]
PER_CONTEXT = 6
HERE = os.path.dirname(os.path.abspath(__file__))
WORDS = os.path.join(HERE, "../../app/src/main/res/raw/en_words.txt")


def rows(name):
    cache = os.path.join(HERE, ".cache", name)
    if not os.path.exists(cache):
        os.makedirs(os.path.dirname(cache), exist_ok=True)
        with urllib.request.urlopen(BASE + name) as r, open(cache, "wb") as f:
            f.write(r.read())
    with open(cache, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            yield row["ngram"], int(row["freq"])


def tokens(ngram):
    """Google splits contractions ("it 's", "do n't"): join them back into one word."""
    out = []
    for t in ngram.split(" "):
        if out and (t.startswith("'") or t == "n't"):
            out[-1] += t
        else:
            out.append(t)
    return out


def main(dest):
    known, quiet = set(), set()
    with open(WORDS, encoding="utf-8") as f:
        for line in f:
            w = line.strip()
            if not w:
                continue
            if w.startswith("!"):
                quiet.add(w[1:])
            else:
                known.add(w)
    word = re.compile(r"[A-Za-z]+('[A-Za-z]+)?")
    counts = collections.defaultdict(collections.Counter)  # context -> next (lowercase) -> freq
    shapes = collections.defaultdict(collections.Counter)  # next (lowercase) -> spelling -> freq
    for name in FILES:
        for ngram, freq in rows(name):
            t = tokens(ngram)
            if len(t) < 2 or not all(word.fullmatch(x) for x in t):
                continue
            low = [x.lower() for x in t]
            if any(x not in known or x in quiet for x in low):
                continue
            nxt = low[-1]
            counts[" ".join(low[:-1])][nxt] += freq
            shapes[nxt][t[-1]] += freq
    def shown(w):
        if w == "i" or w.startswith("i'"):
            return "I" + w[1:]
        best = shapes[w].most_common(1)[0][0]
        # A capital only where the word is usually written that way (York, English), not "The".
        return best if best[0].isupper() and best.lower() != best and shapes[w][best] > sum(shapes[w].values()) * 0.6 else w
    lines = []
    for ctx in sorted(counts):
        nexts = [shown(w) for w, _ in counts[ctx].most_common(PER_CONTEXT)]
        lines.append(ctx + "\t" + " ".join(nexts))
    with open(dest, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{len(lines)} contexts -> {dest}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "en_next.txt")
