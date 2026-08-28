#!/usr/bin/env python3
"""
Menerapkan katalog terjemahan dialog (R4) ke skrip Lua.

⚠️ Penerapan bersifat POSISIONAL, bukan cari-ganti global. Hanya literal
yang berdiri di argumen yang benar-benar tampil di layar (aturan di
`inventaris.py`) yang diganti. Nama barang/mob/NPC yang kebetulan berbunyi
sama TIDAK ikut tersentuh — itulah sebabnya `characterLog.lua` yang menulis
" for " ke berkas log tetap utuh sementara kalimat toko diterjemahkan.

⚠️ Nilai balik `menuString` adalah STRING OPSINYA sendiri, jadi pembanding
`pilihan == "Yes"` HARUS ikut diterjemahkan bersama opsinya — kalau tidak,
menunya berbahasa Indonesia tetapi tidak ada cabang yang cocok, dan gagalnya
SENYAP: NPC hanya diam. Karena itu literal di posisi pembanding ikut
diterjemahkan bila teksnya ada di katalog. (Diperiksa: tidak ada satu pun
`speech == "..."` yang bertabrakan — kata kunci pemain sudah diterjemahkan
lebih dulu, 26 Agustus 2026.)
"""
import os, sys, json, glob, re
sys.path.insert(0, os.path.dirname(__file__))
import inventaris as inv

AKAR = inv.AKAR
HERE = os.path.dirname(__file__)
BANDING = re.compile(r'[=~]=\s*"([^"\\]*(?:\\.[^"\\]*)*)"')


def muat_kamus():
    kamus = {}
    for p in sorted(glob.glob(os.path.join(HERE, 'kamus-*.json'))):
        d = json.load(open(p, encoding='utf-8'))
        for k, v in d.items():
            if k in kamus and kamus[k] != v:
                raise SystemExit(f'kamus bentrok untuk {k!r}: {kamus[k]!r} vs {v!r}')
            kamus[k] = v
    return kamus


def rentang_tampilan(src):
    """(mulai, akhir, teks) setiap literal di posisi TAMPIL.

    ⚠️ Komentar dibuang lebih dulu supaya kode debug yang SUDAH dimatikan
    tidak ikut diterjemahkan. `buang_komentar` mempertahankan panjang, jadi
    offset yang dikembalikan tetap sahih untuk sumber aslinya.
    """
    src = inv.buang_komentar(src)
    lokal = inv.tabel_lokal(src)
    opsi = inv.nama_opsi(src)
    keluar = []
    # Opsi menu yang dibangun bertahap dengan table.insert(opts, "...").
    for m in inv.SISIP.finditer(src):
        if m.group(1) not in opsi:
            continue
        buka = src.index('(', m.start())
        isi, _ = inv.ambil_argumen(src, buka)
        if isi is None:
            continue
        potong = inv.belah_argumen(isi)
        off = len(potong[0]) + 1
        for b in potong[1:]:
            for lm in inv.LITERAL.finditer(b):
                keluar.append((buka + 1 + off + lm.start() + 1,
                               buka + 1 + off + lm.end() - 1, lm.group(1)))
            off += len(b) + 1
    for m in inv.PANGGIL.finditer(src):
        nama = m.group(1)
        if nama not in inv.ATURAN:
            continue
        isi, tutup = inv.ambil_argumen(src, m.end() - 1)
        if isi is None:
            continue
        dasar = m.end()          # tepat sesudah '('
        potong = inv.belah_argumen(isi)
        off = 0
        for i, bagian in enumerate(potong):
            aturan = inv.ATURAN[nama]
            cocok = (i in [a for a in aturan if isinstance(a, int)]
                     or any(isinstance(a, str) and int(a[-1]) == i and '{' in bagian
                            for a in aturan))
            if cocok:
                for lm in inv.LITERAL.finditer(inv.butakan_identifier(bagian)):
                    keluar.append((dasar + off + lm.start() + 1,
                                   dasar + off + lm.end() - 1, lm.group(1)))
            elif any(isinstance(a, str) and int(a[-1]) == i for a in aturan):
                # ⚠️ Daftar opsi yang ditaruh di variabel lebih dulu
                # (`local choices = {...}`) — offsetnya di tempat lain.
                for mulai, isi in inv.tabel_terpakai(lokal, bagian, m.start()):
                    for lm in inv.LITERAL.finditer(inv.butakan_identifier(isi)):
                        keluar.append((mulai + 1 + lm.start() + 1,
                                       mulai + 1 + lm.end() - 1, lm.group(1)))
            off += len(bagian) + 1   # +1 untuk koma
    return keluar


def rentang_banding(src):
    src = inv.buang_komentar(src)
    return [(m.start(1), m.end(1), m.group(1)) for m in BANDING.finditer(src)]


def main():
    tulis = '--tulis' in sys.argv
    kamus = muat_kamus()
    total, per_berkas, tak_dikenal = 0, 0, 0
    for root, dirs, files in os.walk(AKAR):
        for f in sorted(files):
            if not f.endswith('.lua'):
                continue
            p = os.path.join(root, f)
            rel = os.path.relpath(p, AKAR)
            if inv.dikecualikan(rel):
                continue
            src = open(p, encoding='utf-8').read()
            rentang = rentang_tampilan(src) + rentang_banding(src)
            # buang tumpang tindih, urut mundur supaya offset tetap sahih
            rentang = sorted(set(rentang), key=lambda r: -r[0])
            baru, n = src, 0
            dipakai = set()
            for a, b, teks in rentang:
                if (a, b) in dipakai:
                    continue
                dipakai.add((a, b))
                if teks in kamus:
                    baru = baru[:a] + kamus[teks] + baru[b:]
                    n += 1
            if n:
                per_berkas += 1
                total += n
                if tulis:
                    open(p, 'w', encoding='utf-8').write(baru)
    print(f'{total} literal diganti di {per_berkas} berkas '
          f'({"DITULIS" if tulis else "uji coba, pakai --tulis"})')
    print(f'{len(kamus)} entri katalog')


if __name__ == '__main__':
    main()
