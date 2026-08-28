TutorialNpc1 = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		player:dialogSeq(
			{
				t,
				"Untunglah daerah ini relatif aman dijelajahi. Di kerajaan ini berkeliaran banyak binatang yang bisa membunuhmu hanya dengan sekali pandang.\n\nDi padang ini, sebaliknya, sedikit yang bisa membunuhmu dengan cepat... tetapi bukan berarti mereka tidak bisa membunuhmu.",
				"Kau harus membekali diri baik-baik untuk setiap petualangan, dengan salah satu dari ratusan zirah, senjata, dan barang yang tersedia sepanjang hidupmu.\n\nCoba kulihat apa yang bisa kusisihkan untukmu sekarang.\n\nAh ya, ambil barang-barang ini..."
			},
			1
		)

		if (player:hasItem("wooden_saber", 1) ~= true and not player:hasEquipped("wooden_saber")) then
			player:addItem("wooden_saber", 1)
		end

		if (player.sex == 0 and player:hasItem("peasant_garb", 1) ~= true and not player:hasEquipped("peasant_garb")) then
			-- male
			player:addItem("peasant_garb", 1)
		elseif (player.sex == 1 and player:hasItem("peasant_dress", 1) ~= true and not player:hasEquipped("peasant_dress")) then
			--female
			player:addItem("peasant_dress", 1)
		end

		if (Config.tutorialMountEnabled and player:hasItem("horse_mount", 1) ~= true) then
			player:addItem("horse_mount", 1)
		end

		player:dialogSeq(
			{
				t,
				"Untuk melihat isi kantongmu, tekan tombol 'i'.\n\nUntuk memakai senjata atau barang di kantong, klik ganda dengan tetikusmu.\n\nSetelah barangmu terpakai, tekan <spasi> untuk menyerang apa yang ada di depanmu.",
				"Bunuh beberapa kelinci di sekitar sini; barang yang mereka jatuhkan bisa kau pungut dengan berdiri di atasnya lalu menekan tombol ' , ' ((koma)), atau dengan mengkliknya memakai tetikus.",
				"Setibanya di kota, carilah pandai besi untuk menjaga senjatamu tetap prima. Senjata yang dipakai bisa menumpul dan akhirnya patah kalau tidak segera diperbaiki."
			},
			1
		)

		player:dialogSeq(
			{
				t,
				"Untuk keluar dari sini kau harus menemukan pintu keluarnya; lihat kanan bawah layarmu. Di situ ada koordinatmu, dan angkanya berubah saat kau bergerak.\n\nTeruslah berjalan sampai angkanya menunjukkan 0021 0020; di sanalah jalan keluarnya."
			},
			0
		)
	end)
}

TutorialNpc2 = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if (not player:hasSpell("soothe")) then
			player:dialogSeq(
				{
					t,
					"Selamat datang, Nak. Sepertinya kau banyak belajar, dan kurasakan pikiranmu meluas pesat.",
					"Di gubuk sebelahku kau akan menemukan Tominaru dan saudariku Mignok, para pengajar pemula.",
					"Tominaru akan menjelaskan banyak kebijaksanaan tentang Kerajaan-kerajaan ini, dan saudariku akan mengajarkanmu mantra yang sangat berharga. Ia mungkin meminta barang sebagai imbalannya.\n\nKembalilah kepadaku setelah mereka mengajarimu."
				},
				1
			)
		else
			player:dialogSeq(
				{
					t,
					"Ah, kau tampak tidak sabar memakai mantra barumu. Untuk melihat daftar mantramu, tekan tombol '+' pada papan angka di sebelah kanan papan ketikmu. Untuk merapal, cukup klik ganda mantranya. Kalau mantra itu bisa dirapal pada orang lain, kotak sasaran akan muncul dan bisa kau geser dengan tombol panah.",
					"Kau juga bisa memakai \"Makro\" untuk mempercepat perapalan, sehingga kau cukup menekan angka 0 sampai 9 tanpa menekan Z atau huruf mantranya. Aturlah makromu dengan menekan F11; masukkan huruf mantra di sebelah angka yang kau inginkan, lalu cukup tekan angka itu untuk merapal.",
					"Untuk memakai mantra tanpa makro, tekan [shift] + [z] lalu tekan huruf mantranya dari daftar. Cobalah mantra barumu lalu menujulah ke timur."
				},
				1
			)
			if (player.registry["tutorialnpcexp"] == 0) then
				player:giveXP(75)
				player.registry["tutorialnpcexp"] = 1
			end
		end
	end)
}
