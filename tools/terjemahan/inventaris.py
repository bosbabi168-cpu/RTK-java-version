#!/usr/bin/env python3
"""
Inventaris teks DIALOG di skrip Lua (R4).

⚠️ Yang dicari bukan "semua string", melainkan string yang benar-benar
SAMPAI KE LAYAR PEMAIN. Nama barang/mob/NPC di skrip adalah identifier —
menerjemahkannya memutus `addItem` dkk. secara senyap. Karena itu setiap
panggilan punya aturan POSISI argumennya sendiri:

    sendMinitext(teks)              -> arg 0
    talk(ragam, teks)               -> arg 1
    dialogSeq({t, teks...}, n)      -> seluruh string di tabel pertama
    menuSeq(topik, {opsi...}, {})   -> arg 0 + seluruh string tabel opsi
    menuString(pesan, {opsi...})    -> arg 0 + seluruh string tabel opsi
    input(pesan)                    -> arg 0
    addLegend(teks, nama, ...)      -> arg 0 SAJA (arg 1 dibaca hasLegend)

⚠️ Nilai balik `menuString` adalah STRING OPSINYA sendiri, jadi opsi dan
pembandingnya (`if pilihan == "Yes"`) harus diterjemahkan BERSAMAAN.
"""
import os, re, sys, json

AKAR = os.path.join(os.path.dirname(__file__), '..', '..', 'luascript')

# Berkas perkakas: dikecualikan seluruhnya (lihat GLOSARIUM "JANGAN
# DITERJEMAHKAN" — kata yang sama berarti hal berbeda di sini).
def dikecualikan(rel):
    r = rel.replace('\\', '/')
    return ('/Tools/' in r or r.startswith('Tools/')
            or 'God_Tools' in r or 'gm_click' in r
            or r.endswith('Accepted/speech.lua'))

ATURAN = {
    'sendMinitext': [0], 'sendMinitextXY': [0],
    'talk': [1],
    'input': [0],
    'addLegend': [0],
    'dialogSeq': ['tabel0'],
    'menuSeq': [0, 'tabel1'],
    'menuString': [0, 'tabel1'],
    'menu': [0, 'tabel1'],
    'dialog': [0],
}

# ⚠️ Huruf tunggal "a" dan "i" SENGAJA tidak masuk daftar. Kode warna
# RetroTK ditulis "\\a" di sumber Lua, dan literalnya memang memuat huruf a —
# sehingga potongan yang sudah SELESAI diterjemahkan (" \\a Jml: ") tetap
# dihitung "masih Inggris" dan alat ini melapor pekerjaan yang tidak ada.
INGGRIS = re.compile(r"\b(the|you|your|is|are|to|of|and|an|my|have|has|"
                     r"will|what|this|that|for|with|it|not|do|don't|can|we|"
                     r"they|he|she|in|on|at|be|was|were|would|should|there|"
                     r"here|from|but|if|so|no|yes|me|him|her|them|us|our|"
                     r"who|why|how|when|where|all|some|any|more|very|just|"
                     r"now|then|back|come|go|get|give|take|see|know|think|"
                     r"want|need|make|let|say|tell|ask|please|thank|thanks|"
                     r"sorry|hello|good|bad|old|new|young|great|little|"
                     # ⚠️ Kata TUNGGAL yang menjadi opsi menu. Tanpa baris
                     # ini alat melapor "nol sisa" sementara menu yang
                     # BENAR-BENAR dilihat pemain masih berbunyi
                     # "Buy / Sell / Banking" — ketahuan oleh klien
                     # sungguhan, bukan oleh alat ini.
                     r"buy|sell|banking|shout|quit|next|previous|cancel|"
                     r"close|exit|leave|enter|open|help|info|shop|trade|"
                     r"repair|deposit|withdraw|balance|item|items|gold|"
                     r"coins|spell|spells|quest|quests|skill|skills|craft|"
                     r"crafting|nothing|none|other|others|status|options|"
                     r"settings|world|date|time|free|list|view|show|start|"
                     r"stop|join|learn|teach|train|training|group|"   # 'guild' TIDAK di sini:
                     # GLOSARIUM memakainya apa adanya ("Guild Prajurit")
                     r"friend|friends|home|town|city|weapon|weapons|armor|"
                     r"transport|banking|mail|parcel|bank|sale|price)\b",
                    re.I)


def belah_argumen(teks):
    """Belah daftar argumen tingkat atas; kembalikan daftar potongan."""
    bagian, dalam, kedalaman, i, mulai = [], None, 0, 0, 0
    while i < len(teks):
        c = teks[i]
        if dalam:
            if c == '\\':
                i += 2
                continue
            if c == dalam:
                dalam = None
        elif c in '"\'':
            dalam = c
        elif c in '([{':
            kedalaman += 1
        elif c in ')]}':
            kedalaman -= 1
        elif c == ',' and kedalaman == 0:
            bagian.append(teks[mulai:i])
            mulai = i + 1
        i += 1
    bagian.append(teks[mulai:])
    return bagian


def ambil_argumen(src, buka):
    """Isi tanda kurung yang dibuka di indeks `buka`; None bila tak tutup."""
    kedalaman, dalam, i = 0, None, buka
    while i < len(src):
        c = src[i]
        if dalam:
            if c == '\\':
                i += 2
                continue
            if c == dalam:
                dalam = None
        elif c in '"\'':
            dalam = c
        elif c in '([{':
            kedalaman += 1
        elif c in ')]}':
            kedalaman -= 1
            if kedalaman == 0:
                return src[buka + 1:i], i
        i += 1
    return None, None


LITERAL = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')

# ⚠️ Panggilan yang argumennya IDENTIFIER, bukan teks layar. Literal di
# dalamnya harus dibutakan sebelum sebuah tabel dipindai: tabel dialog
# bisa memuat `convertGraphic(723, "item")`, dan "item" di situ adalah
# ruang grafik — menerjemahkannya membuat gambar hilang tanpa error.
IDENT_PANGGIL = re.compile(
    r'\b(convertGraphic|Item|NPC|addItem|hasItem|removeItem|hasLegend|'
    r'setAether|setDuration|hasDuration|killCount|getSkillLevel|'
    r'checkSkillLevel|hasSpell|addSpell|removeSpell)\s*\(')


def butakan_identifier(teks):
    """Ganti isi panggilan identifier dengan spasi; panjang dipertahankan."""
    keluar = list(teks)
    for m in IDENT_PANGGIL.finditer(teks):
        isi, tutup = ambil_argumen(teks, m.end() - 1)
        if isi is None:
            continue
        for k in range(m.end(), tutup):
            if keluar[k] != '\n':
                keluar[k] = ' '
    return ''.join(keluar)



def buang_komentar(src):
    """Ganti isi komentar Lua dengan spasi, panjang dipertahankan.

    ⚠️ Wajib. Tanpa ini alat menghitung kode debug yang SUDAH dimatikan
    sebagai dialog — `--player:talk(0,"id is: "..x)` dan kerabatnya — lalu
    menerjemahkan baris yang tidak pernah dijalankan siapa pun. Panjang
    dipertahankan supaya offset literal tetap sahih untuk penerapan.
    """
    keluar = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c in '"\'':
            kutip = c
            i += 1
            while i < n and src[i] != kutip:
                i += 2 if src[i] == '\\' else 1
            i += 1
            continue
        if c == '-' and i + 1 < n and src[i + 1] == '-':
            # blok --[[ ... ]] atau --[==[ ... ]==]
            m = re.match(r'--\[(=*)\[', src[i:])
            if m:
                tutup = ']' + m.group(1) + ']'
                j = src.find(tutup, i)
                j = n if j < 0 else j + len(tutup)
            else:
                j = src.find('\n', i)
                j = n if j < 0 else j
            for k in range(i, j):
                if keluar[k] != '\n':
                    keluar[k] = ' '
            i = j
            continue
        i += 1
    return ''.join(keluar)
PANGGIL = re.compile(r'(?:^|[^A-Za-z0-9_])([A-Za-z_][A-Za-z0-9_]*)\s*\(')


TABEL_LOKAL = re.compile(r'\blocal\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*\{')


def tabel_lokal(src):
    """Peta nama -> isi tabel untuk `local nama = { ... }`.

    ⚠️ Tanpa ini seluruh daftar opsi menu yang ditaruh di variabel lebih
    dulu (`local choices = {...}` lalu `menuSeq(topik, choices, {})`)
    TIDAK TERLIHAT — dan alat melaporkan "nol sisa" untuk berkas yang
    masih penuh teks Inggris. Ditemukan gara-gara satu string Inggris
    muncul di pencarian lain, bukan oleh alat ini sendiri.
    """
    peta = {}
    for m in TABEL_LOKAL.finditer(src):
        isi, tutup = ambil_argumen(src, m.end() - 1)
        if isi is not None:
            peta.setdefault(m.group(1), []).append((m.end() - 1, isi))
    return peta


SISIP = re.compile(r'\btable\.insert\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*,')


def nama_opsi(src):
    """Nama variabel yang pernah diserahkan sebagai DAFTAR OPSI menu.

    ⚠️ Opsi menu sering dibangun bertahap: `local opts = {...}` lalu
    `table.insert(opts, "Free World Shout")`. Literal di dalam
    `table.insert` itu tampil di layar pemain, tetapi tidak berada di
    dalam argumen panggilan menu mana pun — konstruksi ketiga yang
    lolos dari alat ini sampai klien sungguhan menampilkannya.
    """
    nama = set()
    for m in PANGGIL.finditer(src):
        if m.group(1) not in ATURAN:
            continue
        isi, _ = ambil_argumen(src, m.end() - 1)
        if isi is None:
            continue
        args = belah_argumen(isi)
        for aturan in ATURAN[m.group(1)]:
            if isinstance(aturan, str):
                i = int(aturan[-1])
                if i < len(args) and '{' not in args[i]:
                    nama.add(args[i].strip())
    return nama


def titik_dialog(src):
    """Hasilkan (nama_panggilan, teks) untuk teks tampilan; komentar dibuang."""
    src = buang_komentar(src)
    lokal = tabel_lokal(src)
    opsi = nama_opsi(src)
    keluar = []
    for m in SISIP.finditer(src):
        if m.group(1) not in opsi:
            continue
        isi, _ = ambil_argumen(src, m.end() - len(m.group(0)) + m.group(0).index('('))
        if isi is None:
            continue
        bagian = belah_argumen(isi)
        for b in bagian[1:]:
            for lm in LITERAL.finditer(b):
                keluar.append(('table.insert/opsi', lm.group(1)))
    for m in PANGGIL.finditer(src):
        nama = m.group(1)
        if nama not in ATURAN:
            continue
        isi, tutup = ambil_argumen(src, m.end() - 1)
        if isi is None:
            continue
        args = belah_argumen(isi)
        for aturan in ATURAN[nama]:
            if isinstance(aturan, int):
                if aturan < len(args):
                    for lm in LITERAL.finditer(args[aturan]):
                        # hanya literal yang berdiri sendiri sebagai argumen
                        keluar.append((nama, lm.group(1)))
            else:
                idx = int(aturan[-1])
                if idx >= len(args):
                    continue
                bagian = args[idx]
                if '{' in bagian:
                    for lm in LITERAL.finditer(butakan_identifier(bagian)):
                        keluar.append((nama + '/opsi', lm.group(1)))
                else:
                    # Argumen berupa NAMA variabel: telusuri tabel lokal
                    # terdekat SEBELUM panggilan ini.
                    nm = bagian.strip()
                    for isi in terdekat(lokal.get(nm), m.start()):
                        for lm in LITERAL.finditer(butakan_identifier(isi)):
                            keluar.append((nama + '/opsi', lm.group(1)))
    return keluar


def dikecualikan_teks():
    """Teks tampilan yang sengaja DIBIARKAN Inggris (nama barang/stat)."""
    f = os.path.join(os.path.dirname(__file__), 'dikecualikan.json')
    if not os.path.isfile(f):
        return set()
    return set(json.load(open(f, encoding='utf-8'))['nama'])


def nilai_kamus():
    """Seluruh TERJEMAHAN yang sudah dihasilkan.

    ⚠️ Dipakai untuk mengecualikan hasil kerja sendiri dari hitungan
    "masih Inggris". Terjemahan yang benar tetap memuat nama diri Inggris
    ('Do Training Arena', 'fragile orb of world shout'), dan penyaring kata
    Inggris akan mengiranya belum diterjemahkan — alat yang melapor
    pekerjaan yang sebenarnya sudah selesai.
    """
    import glob as _glob
    nilai = set()
    for p in _glob.glob(os.path.join(os.path.dirname(__file__), 'kamus-*.json')):
        nilai.update(json.load(open(p, encoding='utf-8')).values())
    return nilai


def tabel_terpakai(lokal, bagian, sebelum):
    """(offset_kurung_buka, isi) tabel lokal yang dirujuk argumen ini."""
    nm = bagian.strip()
    daftar = lokal.get(nm)
    if not daftar:
        return []
    pilih = [(off, isi) for off, isi in daftar if off < sebelum]
    return [pilih[-1]] if pilih else []


def terdekat(daftar, sebelum):
    """Isi tabel lokal terakhir yang dideklarasikan sebelum offset ini."""
    if not daftar:
        return []
    pilih = [isi for off, isi in daftar if off < sebelum]
    return [pilih[-1]] if pilih else []


def main():
    # ⚠️ Termasuk yang SENGAJA dibiarkan Inggris (nama barang/stat),
    # supaya angka 'masih Inggris' berarti 'belum dikerjakan', bukan
    # 'belum diputuskan'.
    sudah = nilai_kamus() | dikecualikan_teks()
    hasil = {}
    for root, dirs, files in os.walk(AKAR):
        for f in sorted(files):
            if not f.endswith('.lua'):
                continue
            p = os.path.join(root, f)
            rel = os.path.relpath(p, AKAR)
            if dikecualikan(rel):
                continue
            src = open(p, encoding='utf-8', errors='replace').read()
            titik = titik_dialog(src)
            inggris = [t for _, t in titik
                       if INGGRIS.search(t) and t not in sudah]
            if titik:
                hasil[rel] = {'titik': len(titik), 'inggris': len(inggris)}
    total_t = sum(v['titik'] for v in hasil.values())
    total_i = sum(v['inggris'] for v in hasil.values())
    print(f'berkas berdialog : {len(hasil)}')
    print(f'titik dialog     : {total_t}')
    print(f'masih Inggris    : {total_i}')
    print()
    print('30 berkas dengan sisa Inggris terbanyak:')
    for rel, v in sorted(hasil.items(), key=lambda kv: -kv[1]['inggris'])[:30]:
        print(f"  {v['inggris']:5d} / {v['titik']:5d}  {rel}")
    if len(sys.argv) > 1 and sys.argv[1] == '--json':
        json.dump(hasil, open('inventaris.json', 'w'), indent=1)


if __name__ == '__main__':
    main()
