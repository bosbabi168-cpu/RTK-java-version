#!/usr/bin/env python3
"""
Memeriksa PETUNJUK KETIK: kalimat yang menyuruh pemain mengucapkan atau
mengetik sesuatu harus menyebut kata kunci `speech` yang benar-benar
diterima NPC.

⚠️ Ini pemeriksaan yang paling mudah dilupakan dan paling senyap kalau
salah. Kata kunci `speech` sudah diterjemahkan 26 Agustus 2026; kalau
kalimat prosa yang MENYURUH pemain mengetiknya masih menyebut kata
Inggris, questnya tidak bisa diselesaikan — NPC tetap menjawab, hanya
tidak pernah pada kata yang dianjurkannya sendiri.
"""
import os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import inventaris as inv

PERINTAH = re.compile(r'\b(ucapkan|katakan|sebutkan|ketik|tanyakan|bilang)\b', re.I)
KUTIP = re.compile(r'[\'"“]([A-Za-z][A-Za-z \'-]{1,30})[\'"”]')

# Kata kunci speech yang benar-benar dibandingkan di korpus.
kunci = set()
for root, dirs, files in os.walk(inv.AKAR):
    for f in files:
        if not f.endswith('.lua'):
            continue
        src = inv.buang_komentar(open(os.path.join(root, f), encoding='utf-8').read())
        for m in re.finditer(r'speech\s*==\s*"([^"]*)"', src):
            kunci.add(m.group(1).lower())
        for m in re.finditer(r'lspeech\s*==\s*"([^"]*)"', src):
            kunci.add(m.group(1).lower())

curiga = []
for root, dirs, files in os.walk(inv.AKAR):
    for f in sorted(files):
        if not f.endswith('.lua'):
            continue
        p = os.path.join(root, f)
        rel = os.path.relpath(p, inv.AKAR)
        if inv.dikecualikan(rel):
            continue
        for _, t in inv.titik_dialog(open(p, encoding='utf-8').read()):
            if not PERINTAH.search(t):
                continue
            for m in KUTIP.finditer(t):
                kata = m.group(1).strip().lower()
                if kata and kata not in kunci:
                    curiga.append((rel, kata, t[:90]))

print(f'{len(kunci)} kata kunci speech di korpus')
print(f'{len(curiga)} petunjuk ketik yang katanya BUKAN kata kunci:')
for rel, kata, t in curiga:
    print(f'  {rel}: {kata!r}  <- {t}')
