#!/usr/bin/env python3
"""Generate a Polyglot-style binary opening book from a list of (fen_prefix, [(move_uci, weight), ...]) entries."""

import hashlib
import struct
import sys

# Header format: 8-byte magic + 4-byte entry count + 4-byte string pool size
HEADER_FMT = "!8sII"
# Entry format: 4-byte hash (upper 32 bits of SHA-256) + 2-byte string offset + 2-byte weight + 2-byte padding
ENTRY_FMT = "!IHHH"

def compute_hash(fen_board):
    """Compute hash from FEN board part (matches Kotlin implementation)."""
    digest = hashlib.sha256(fen_board.encode('utf-8')).digest()
    # First 4 bytes as big-endian unsigned int (matches Kotlin's int.toLong() & 0xFFFFFFFFL)
    return struct.unpack("!I", digest[:4])[0]

def generate_book(entries, output_file):
    """
    entries: list of (fen_board_part, [(move_uci, weight), ...])
    """
    # Build string pool
    string_pool = b""
    string_offsets = {}  # move_uci -> offset in pool
    all_strings = []

    # First pass: collect all unique move strings
    for _, moves in entries:
        for move_uci, _ in moves:
            if move_uci not in string_offsets:
                string_offsets[move_uci] = len(string_pool)
                encoded = move_uci.encode('utf-8') + b'\x00'
                string_pool += encoded
                all_strings.append(move_uci)

    # Second pass: build entries
    binary_entries = []
    for fen_board, moves in entries:
        h = compute_hash(fen_board)
        for move_uci, weight in moves:
            offset = string_offsets[move_uci]
            binary_entries.append((h, offset, weight))

    # Sort by hash
    binary_entries.sort(key=lambda e: e[0])

    # Write binary file
    entry_count = len(binary_entries)
    string_pool_size = len(string_pool)

    with open(output_file, 'wb') as f:
        # Header
        f.write(struct.pack(HEADER_FMT, b"PGBOOK00", entry_count, string_pool_size))
        # String pool
        f.write(string_pool)
        # Entries
        for h, offset, weight in binary_entries:
            f.write(struct.pack(ENTRY_FMT, h, offset, weight, 0))

    print(f"Generated {output_file}: {entry_count} entries, {string_pool_size} bytes string pool")

def generate_test_book():
    """Generate a test book from common openings."""
    entries = [
        # Starting position
        ("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", [
            ("e2e4", 40),
            ("d2d4", 30),
            ("g1f3", 15),
            ("c2c4", 10),
            ("b1c3", 5),
        ]),
        # 1. e4
        ("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", [
            ("e7e5", 35),
            ("c7c5", 30),
            ("e7e6", 15),
            ("c7c6", 10),
            ("d7d6", 5),
            ("g8f6", 5),
        ]),
        # 1. d4
        ("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR", [
            ("g8f6", 40),
            ("d7d5", 30),
            ("d7d6", 10),
            ("e7e6", 10),
            ("f7f5", 5),
            ("c7c5", 5),
        ]),
        # 1. e4 e5
        ("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR", [
            ("g1f3", 50),
            ("f1c4", 20),
            ("f1b5", 15),
            ("d2d4", 10),
            ("b1c3", 5),
        ]),
        # 1. e4 c5 (Sicilian)
        ("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR", [
            ("g1f3", 40),
            ("d2d4", 25),
            ("b1c3", 15),
            ("c2c3", 10),
            ("f2f4", 5),
            ("b2b4", 5),
        ]),
        # 1. e4 e6 (French)
        ("rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR", [
            ("d2d4", 60),
            ("d2d3", 15),
            ("g1f3", 10),
            ("b1c3", 10),
            ("f2f4", 5),
        ]),
        # 1. e4 c6 (Caro-Kann)
        ("rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR", [
            ("d2d4", 55),
            ("d2d3", 15),
            ("g1f3", 15),
            ("b1c3", 10),
            ("f2f4", 5),
        ]),
        # 1. e4 e5 2. Nf3
        ("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", [
            ("b8c6", 40),
            ("d7d6", 20),
            ("g8f6", 20),
            ("d7d5", 10),
            ("f8c5", 10),
        ]),
        # 1. e4 e5 2. Nf3 Nc6
        ("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", [
            ("f1b5", 35),
            ("f1c4", 30),
            ("d2d4", 20),
            ("b1c3", 10),
            ("d1e2", 5),
        ]),
        # 1. d4 d5
        ("rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR", [
            ("c2c4", 55),
            ("g1f3", 20),
            ("b1c3", 15),
            ("c1f4", 5),
            ("e2e3", 5),
        ]),
        # 1. d4 Nf6
        ("rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR", [
            ("c2c4", 45),
            ("g1f3", 25),
            ("c1g5", 10),
            ("b1c3", 10),
            ("c1f4", 5),
            ("f2f3", 5),
        ]),
        # 1. d4 d5 2. c4
        ("rnbqkbnr/ppp1pppp/8/2pp4/2PP4/8/PP2PPPP/RNBQKBNR", [
            ("e7e6", 30),
            ("d5c4", 25),
            ("c7c6", 20),
            ("c7c5", 15),
            ("g8f6", 5),
            ("d7d6", 5),
        ]),
        # 1. Nf3
        ("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R", [
            ("d7d5", 25),
            ("g8f6", 30),
            ("c7c5", 20),
            ("d7d6", 10),
            ("e7e6", 10),
            ("g7g6", 5),
        ]),
        # 1. c4
        ("rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR", [
            ("e7e5", 25),
            ("c7c5", 20),
            ("g8f6", 20),
            ("e7e6", 15),
            ("g7g6", 10),
            ("d7d6", 5),
            ("c7c6", 5),
        ]),
    ]

    output = sys.argv[1] if len(sys.argv) > 1 else "book.bin"
    generate_book(entries, output)

if __name__ == "__main__":
    generate_test_book()
