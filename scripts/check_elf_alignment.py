#!/usr/bin/env python3
"""Checks that every native .so inside an APK/AAB has ELF LOAD segments aligned to >= 16 KB.

Usage: check_elf_alignment.py <file.apk|file.aab> [...]
Exit code 1 if any 64-bit library is aligned below 16 KB. Prints "NO_NATIVE_LIBS" when none exist.
"""
import struct
import sys
import zipfile

REQUIRED = 16 * 1024


def load_alignments(data: bytes):
    if data[:4] != b"\x7fELF":
        return None, []
    is64 = data[4] == 2
    endian = "<" if data[5] == 1 else ">"
    if is64:
        phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 0x36)
    else:
        phoff = struct.unpack_from(endian + "I", data, 0x1C)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 0x2A)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        if p_type != 1:  # PT_LOAD
            continue
        if is64:
            p_align = struct.unpack_from(endian + "Q", data, off + 0x30)[0]
        else:
            p_align = struct.unpack_from(endian + "I", data, off + 0x1C)[0]
        aligns.append(p_align)
    return is64, aligns


def main(paths):
    found = False
    failed = False
    for path in paths:
        with zipfile.ZipFile(path) as z:
            for info in z.infolist():
                if not info.filename.endswith(".so"):
                    continue
                found = True
                is64, aligns = load_alignments(z.read(info))
                ok = bool(aligns) and min(aligns) >= REQUIRED
                status = "OK" if ok else ("FAIL" if is64 else "INFO(32-bit)")
                if not ok and is64:
                    failed = True
                print(f"{status}\t{path}!{info.filename}\tLOAD align={aligns}\tcompressed={info.compress_type != 0}")
    if not found:
        print("NO_NATIVE_LIBS")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
