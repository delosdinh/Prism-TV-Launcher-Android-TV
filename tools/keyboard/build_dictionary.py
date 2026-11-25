import wordfreq, re, sys
N = 60000
words = wordfreq.top_n_list('en', N)
ok = [x for x in words if re.fullmatch(r"[a-z]+('[a-z]+)?", x) and len(x) <= 18]
z = {w: wordfreq.zipf_frequency(w, 'en') for w in ok}
inset = set(ok)
letters = 'abcdefghijklmnopqrstuvwxyz'
def edits1(w):
    s = [(w[:i], w[i:]) for i in range(len(w)+1)]
    out = set()
    for a,b in s:
        if b: out.add(a+b[1:])
        if len(b)>1: out.add(a+b[1]+b[0]+b[2:])
        for c in letters:
            if b: out.add(a+c+b[1:])
            out.add(a+c+b)
    out.discard(w)
    return out
# Contractions written without the apostrophe ("dont", "im") are typos to correct, not words.
contr = set("dont im ive youre theyre didnt doesnt isnt wasnt wouldnt couldnt shouldnt havent hasnt arent werent thats whats youve youll theyve theyll wouldve couldve shouldve hes shes theres heres wheres aint cant wont weve hadnt mustnt neednt yall".split())
drop = set()
for w in ok:
    if w in contr:
        drop.add(w); continue
# Common misspellings that are frequent enough on the web to sneak into the list.
drop |= set("teh alot thier recieve definately seperate untill wich becuase occured accross goverment tommorow wierd".split())
# Valid (never "corrected") but never offered as a suggestion: profanity, slurs and explicit terms.
quiet = set(open('quiet.txt').read().split())
final = [("!" + w if w in quiet else w) for w in ok if w not in drop]
final = final[:56000]
open(sys.argv[1], 'w').write('\n'.join(final) + '\n')
print(len(ok), len(drop), len(final), sum(1 for w in final if w.startswith('!')))
