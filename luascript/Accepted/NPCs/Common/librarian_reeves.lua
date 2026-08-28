LibrarianReevesNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		player:dialogSeq({t, "Halo, ada yang bisa kubantu?"}, 1)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] >= 0 then
			if speech == "sendok" then
				if player.quest["reeves_quest"] == 0 then
					player.quest["reeves_quest"] = 1
				end
				player:dialogSeq(
					{
						t,
						"Ah ya, Spoon. Orang yang menarik. Tadi ia ke sini, bersemangat sekali ingin tahu lebih banyak soal iblis, patung, dan sayap ayam pedas... entah apa maksudnya."
					},
					0
				)
			end
		end

		if player.quest["reeves_quest"] >= 1 then
			if speech == "iblis" then
				if player.quest["reeves_quest"] == 1 then
					player.quest["reeves_quest"] = 2
				end
				player:dialogSeq(
					{
						t,
						"Spoon sangat bersemangat mendapatkan segala bahan yang kami punya tentang iblis.",
						"Meski catatan faktual kami tentang iblis tidak banyak, ia tampak tertarik pada desas-desus maupun kesaksian tertulis tentang makhluk keji itu.",
						"Coba kuingat buku apa yang ia tekuni berjam-jam di bawah cahaya lilin."
					},
					0
				)
			end
			if speech == "iblis" then
				if player.quest["reeves_quest"] == 1 then
					player.quest["reeves_quest"] = 2
				end
				player:dialogSeq(
					{
						t,
						"Spoon sangat bersemangat mendapatkan segala bahan yang kami punya tentang iblis.",
						"Meski catatan faktual kami tentang iblis tidak banyak, ia tampak tertarik pada desas-desus maupun kesaksian tertulis tentang makhluk keji itu.",
						"Coba kuingat buku apa yang ia tekuni berjam-jam di bawah cahaya lilin."
					},
					0
				)
			end
			if speech == "sayap ayam pedas" then
				player:dialogSeq(
					{
						t,
						"Sulit kupercaya, ia bilang orang lain juga akan tertarik pada resep sayap ayamnya.",
						"Resepnya ia titipkan padaku, setelah berjam-jam bicara soal keseimbangan antara saus pedas dan bubuk bawang putih... sejujurnya aku belum pernah sebosan itu. Pokoknya, ini dia!",
						"Spoon's Spicy Chicken Wings",
						"Persiapan: 15-20 menit | Memasak: 45 menit | Siap dalam ~2 jam",
						"3/4 cangkir tepung serbaguna, 1/2 sendok teh cabai rawit bubuk, 1/2 sendok teh bubuk bawang putih, 1/2 sendok teh garam, 1/2 cangkir mentega leleh, dan saus pedas sebanyak yang sanggup kau tahan",
						"Lapisi loyang dengan kertas aluminium dan olesi tipis dengan minyak semprot. Masukkan tepung, cabai rawit bubuk, bubuk bawang putih, dan garam ke kantong plastik berpenutup, lalu kocok sampai tercampur. Masukkan sayap ayam, tutup, dan guncang sampai terbalut rata.",
						"Tata sayapnya di loyang yang sudah disiapkan lalu masukkan ke lemari es. Dinginkan sedikitnya 1 jam.",
						"Panaskan oven sampai 400 derajat F (200 derajat C).",
						"Kocok mentega leleh dan saus pedas dalam mangkuk kecil. Celupkan sayapnya ke campuran mentega lalu kembalikan ke loyang.",
						"Panggang dalam oven yang sudah panas sampai bagian tengah ayamnya tidak lagi merah muda dan luarnya renyah, sekitar 45 menit. Balik sayapnya di tengah waktu memanggang supaya matang merata.",
						"Padukan dengan IPA. Selamat menikmati! ~GM Spoon"
					},
					0
				)
			end
		end

		if player.quest["reeves_quest"] >= 1 then
			if speech == "patung" then
				player:dialogSeq(
					{
						t,
						"Oh, aku bisa bicara soal patung berhari-hari! Dulu kerajaan ini penuh patung para pahlawan kami...",
						"Sayangnya penguasa kami memutuskan tidak boleh ada patung di dalam kerajaan selain Totem.",
						"Yah, semoga saja ia berubah pikiran."
					},
					0
				)
			end
			if speech == "patung" then
				player:dialogSeq(
					{
						t,
						"Oh, aku bisa bicara soal patung berhari-hari! Dulu kerajaan ini penuh patung para pahlawan kami...",
						"Sayangnya penguasa kami memutuskan tidak boleh ada patung di dalam kerajaan selain Totem.",
						"Yah, semoga saja ia berubah pikiran."
					},
					0
				)
			end
		end

		if player.quest["reeves_quest"] >= 2 then
			if speech == "buku" then
				if player.quest["reeves_quest"] == 2 then
					player.quest["reeves_quest"] = 3
				end
				player:dialogSeq(
					{
						t,
						"Kami punya cukup banyak buku tentang ghoul dan goblin, juga iblis.",
						"Spoon tampak agak cemas soal sebuah buku yang menyebut... oh apa tadi... oh ya, 'The Calamity'.",
						"Kalau tidak salah bukunya ada di sana, di sebelah asistenku, Yan. Coba tanyakan kepadanya."
					},
					0
				)
			end
		end
	end)
}
