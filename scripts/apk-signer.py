#!/usr/bin/env python3
"""Prints the SHA-256 of an APK's signing certificate (APK Signature Scheme v3, else v2).

Same value as `apksigner verify --print-certs` ("Signer #1 certificate SHA-256 digest"), without
needing the Android build tools. Usage: apk-signer.py <file.apk>
"""
import hashlib
import struct
import sys

V2, V3 = 0x7109871A, 0xF05368C0


def lp(buf, off):
    """A uint32 length-prefixed field at off → (field, next offset)."""
    (n,) = struct.unpack_from("<I", buf, off)
    return buf[off + 4 : off + 4 + n], off + 4 + n


def signer_sha256(path):
    data = open(path, "rb").read()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise SystemExit("not a zip/APK")
    (cd_off,) = struct.unpack_from("<I", data, eocd + 16)
    if data[cd_off - 16 : cd_off] != b"APK Sig Block 42":
        raise SystemExit("no APK Signing Block (v1-only signature?)")
    (size,) = struct.unpack_from("<Q", data, cd_off - 24)
    pairs = data[cd_off - size - 8 + 8 : cd_off - 24]
    blocks, i = {}, 0
    while i + 12 <= len(pairs):
        (ln,) = struct.unpack_from("<Q", pairs, i)
        (pid,) = struct.unpack_from("<I", pairs, i + 8)
        blocks[pid] = pairs[i + 12 : i + 8 + ln]
        i += 8 + ln
    for pid in (V3, V2):
        if pid in blocks:
            signers, _ = lp(blocks[pid], 0)
            signer, _ = lp(signers, 0)
            signed, _ = lp(signer, 0)
            _digests, off = lp(signed, 0)
            certs, _ = lp(signed, off)
            cert, _ = lp(certs, 0)
            return hashlib.sha256(cert).hexdigest()
    raise SystemExit("no v2/v3 signature")


if __name__ == "__main__":
    print(signer_sha256(sys.argv[1]))
