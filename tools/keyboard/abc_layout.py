#!/usr/bin/env python3
"""
How many D-pad moves Prism Keyboard's letter layouts take for common English text.

Text model: the 30,000 most common words in app/src/main/res/raw/en_words.txt, weighted by Zipf's law
(frequency ~ 1 / (rank + 10)), with a space between words. Every letter-to-letter and letter-to-space
step is weighted by how often it occurs, and costed as the fewest remote presses between the two keys
using the keyboard's real navigation: Left/Right wrap round a row, Up/Down move to the nearest key by
centre and wrap top-to-bottom, and the three-slot suggestion bar sits above the top row (it shows
nearly all the time while you type, so it is always counted). Pressing Select is one more per letter.

Usage: python3 tools/keyboard/abc_layout.py
"""
import collections
import os
import string
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
WORDS = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "raw", "en_words.txt")
SP = " "


def transitions(limit=30000):
    words = [line.strip().lstrip("!").lower() for line in open(WORDS, encoding="utf-8")][:limit]
    t = collections.Counter()
    for rank, w in enumerate(words):
        if not (w.isascii() and w.isalpha()):
            continue
        f = 1.0 / (rank + 10)
        for a, b in zip(w, w[1:]):
            t[(a, b)] += f
        t[(w[-1], SP)] += f
        t[(SP, w[0])] += f
    return t


def distances(rows, indent=None, units=10.0, slots=3):
    indent = indent or [0.0] * len(rows)
    last = len(rows) - 1

    def centre(r, c):
        return indent[r] + sum(k[1] for k in rows[r][:c]) + rows[r][c][1] / 2

    def nearest(r, x):
        return min(range(len(rows[r])), key=lambda c: (abs(centre(r, c) - x), c))

    def slot_for(x):
        return max(0, min(slots - 1, int(x / (units / 3))))

    def slot_x(s):
        return s * (units / 3) + units / 6

    def moves(node):
        r, c = node
        if r == -1:
            return [(-1, (c - 1) % slots), (-1, (c + 1) % slots), (last, nearest(last, slot_x(c))), (0, nearest(0, slot_x(c)))]
        n, x = len(rows[r]), centre(r, c)
        up = (-1, slot_for(x)) if r == 0 else (r - 1, nearest(r - 1, x))
        down = (r + 1, nearest(r + 1, x)) if r < last else (-1, slot_for(x))
        return [(r, (c - 1) % n), (r, (c + 1) % n), up, down]

    nodes = [(r, c) for r in range(len(rows)) for c in range(len(rows[r]))] + [(-1, s) for s in range(slots)]
    dist = {}
    for s in nodes:
        d, queue = {s: 0}, [s]
        for u in queue:
            for v in moves(u):
                if v not in d:
                    d[v] = d[u] + 1
                    queue.append(v)
        dist[s] = d
    where = {}
    for r, row in enumerate(rows):
        for c, k in enumerate(row):
            where.setdefault(k[0], (r, c))
    return where, dist


def score(rows, t, indent=None):
    where, dist = distances(rows, indent)
    total = sum(t.values())
    return sum(f * dist[where[a]][where[b]] for (a, b), f in t.items()) / total


def keys(s, u=1.0):
    return [(ch, u) for ch in s]


QWERTY_BOTTOM = [("123", 1.25), ("emoji", 1), (SP, 4), ("mic", 1), ("clear", 1), ("return", 1.75)]
QWERTY = [keys("qwertyuiop"), keys("asdfghjkl"), [("shift", 1.5)] + keys("zxcvbnm") + [("delete", 1.5)], QWERTY_BOTTOM]
U7 = 10 / 7
FUNCTION_ROW = [("shift", 2), ("123", 2), ("emoji", 2), ("mic", 2), ("return", 2)]
LAYOUTS = {
    "QWERTY (Prism's default)": (QWERTY, [0, 0.5, 0, 0]),
    "ABC, 10 a row, QWERTY's bottom row": ([keys("abcdefghij"), keys("klmnopqrst"), keys("uvwxyz") + [("shift", 2), ("delete", 2)], QWERTY_BOTTOM], None),
    "ABC, 9 a row, QWERTY's bottom row": ([keys("abcdefghi"), keys("jklmnopqr"), keys("stuvwxyz") + [("delete", 2)], QWERTY_BOTTOM], None),
    "ABC, 7 a row, keys at the row ends": ([keys("abcdefg") + [("delete", 3)], keys("hijklmn") + [(SP, 3)], keys("opqrstu") + [("shift", 1.5), ("return", 1.5)], keys("vwxyz") + [("123", 1.5), ("emoji", 1.25), ("mic", 1.25), ("'", 1)]], None),
    "ABC, 6 a row + function row (6 rows)": ([keys("abcdef"), keys("ghijkl"), keys("mnopqr"), keys("stuvwx"), keys("yz") + [(SP, 1)] + [("delete", 1)] + [("x1", 1), ("x2", 1)], FUNCTION_ROW], None),
    "ABC, 7 a row, space after m + function row (first draft)": ([keys("abcdefg", U7), keys("hijklm", U7) + [(SP, U7)], keys("nopqrst", U7), keys("uvwxyz", U7) + [("delete", U7)], FUNCTION_ROW], None),
    "ABC like tvOS: switches on top, 6 a row, space/delete/clear after y z (Prism's ABC)": ([[("abc", 10 / 6), ("123", 10 / 6), ("#+=", 10 / 6), ("emoji", 10 / 6), ("mic", 10 / 6), ("return", 10 / 6)], keys("abcdef", 10 / 6), keys("ghijkl", 10 / 6), keys("mnopqr", 10 / 6), keys("stuvwx", 10 / 6), keys("yz", 10 / 6) + [(SP, 20 / 6), ("delete", 10 / 6), ("clear", 10 / 6)]], None),
}

if __name__ == "__main__":
    t = transitions()
    for name, (rows, indent) in LAYOUTS.items():
        m = score(rows, t, indent)
        print(f"{m:5.2f} moves, {m + 1:5.2f} presses per character   {name}")
    sys.exit(0)
