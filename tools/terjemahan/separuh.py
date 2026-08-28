#!/usr/bin/env python3
"""
Mencari kalimat yang SEPARUH TERJEMAH — baris yang memuat literal
tampilan sudah-Indonesia DAN literal tampilan masih-Inggris sekaligus.

⚠️ Ini bahaya khas potongan sambungan: `"Your " .. x .. " skill increases."`
diterjemahkan sebelah saja menghasilkan "" .. x .. " skill increases." —
tidak melempar apa pun, cuma jelek. Jalankan setiap kali menambah entri.
"""
import os, sys, json
sys.path.insert(0, os.path.dirname(__file__))
import inventaris as inv, terapkan as T

kamus = T.muat_kamus()
temuan = []
for root, dirs, files in os.walk(inv.AKAR):
    for f in sorted(files):
        if not f.endswith('.lua'):
            continue
        p = os.path.join(root, f)
        rel = os.path.relpath(p, inv.AKAR)
        if inv.dikecualikan(rel):
            continue
        src = open(p, encoding='utf-8').read()
        garis = [0]
        for i, c in enumerate(src):
            if c == '\n':
                garis.append(i + 1)
        import bisect
        per_baris = {}
        for a, b, teks in T.rentang_tampilan(src):
            ln = bisect.bisect_right(garis, a)
            per_baris.setdefault(ln, []).append(teks)
        for ln, daftar in sorted(per_baris.items()):
            kena = [t for t in daftar if t in kamus]
            sisa = [t for t in daftar if t not in kamus and inv.INGGRIS.search(t)]
            if kena and sisa:
                temuan.append((rel, ln, sisa))
print(f'{len(temuan)} baris separuh terjemah')
for rel, ln, sisa in temuan[:40]:
    print(f'  {rel}:{ln}  {sisa}')
