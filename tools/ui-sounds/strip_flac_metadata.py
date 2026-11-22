"""Drop PADDING and VORBIS_COMMENT metadata blocks from FLAC files (lossless; audio frames untouched)."""
import sys

def strip(path):
    data = open(path, 'rb').read()
    assert data[:4] == b'fLaC', path
    pos, blocks = 4, []
    while True:
        hdr = data[pos]
        last, btype = hdr & 0x80, hdr & 0x7F
        length = int.from_bytes(data[pos + 1:pos + 4], 'big')
        blocks.append((btype, data[pos + 4:pos + 4 + length]))
        pos += 4 + length
        if last:
            break
    frames = data[pos:]
    kept = [(t, b) for t, b in blocks if t not in (1, 4)]
    out = bytearray(b'fLaC')
    for i, (t, b) in enumerate(kept):
        out.append(t | (0x80 if i == len(kept) - 1 else 0))
        out += len(b).to_bytes(3, 'big') + b
    out += frames
    open(path, 'wb').write(out)
    return [t for t, _ in blocks], [t for t, _ in kept], len(data), len(out)

for p in sys.argv[1:]:
    print(p, *strip(p))
