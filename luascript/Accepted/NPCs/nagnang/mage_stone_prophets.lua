MageStoneProphetsNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local mobName = ""

		--attacker.quest["zapped_"..mob.yname]

		if npc.mapTitle == "Prophet Yin" then
			mobName = "yin_mouse"
		elseif npc.mapTitle == "Prophet Yang" then
			mobName = "yang_mouse"
		elseif npc.mapTitle == "Prophet Void" then
			mobName = "void_mouse"
		end

		if player.quest["zapped_" .. mobName] == 0 then
			player:dialogSeq(
				{
					t,
					"Kau belum menunjukkan persembahan. Aku tidak akan berbicara denganmu."
				},
				0
			)
			return
		end

		if npc.mapTitle == "Prophet Yin" then
			player:dialogSeq(
				{
					t,
					"Ah, seseorang yang menunjukkan kebijaksanaan besar dan mengikuti petunjuk sederhana. Itu baik bagimu.",
					"Kau ingin menjadi salah satu Nagnang Mage? Itu lebih soal menjadi mage dalam hati daripada soal asal kota.",
					"Sihir bukan soal membunuh dan menghancurkan. Ia juga soal belas kasih dan keindahan. Inilah tugas yang kuberikan padamu.",
					"Temukan setangkai mawar. Itulah kesederhanaan dan keindahan permintaanku. Simpan mawar itu dan berikan kepada Wand setelah kau menuruti permintaan yang lain."
				},
				0
			)
		end

		if npc.mapTitle == "Prophet Yang" then
			player:dialogSeq(
				{
					t,
					"Kulihat kau sudah belajar cara menyapa kami di gua. Bagus. Bagus.",
					"Jadi kau menginginkan kekuatan yang datang bersama Mage Nagnang? Memang kekuatanlah inti kami.",
					"Sihir punya sisi lembut, tetapi ia juga punya kekuatan dan kuasa untuk menghancurkan dan menaklukkan. Ia bisa lebih kuat daripada senjata atau bilah mana pun.",
					"Untuk membuktikannya, dapatkan sekeping high Ore, dan simpan sampai kau menuntaskan seluruh tugas yang lain lalu kembali ke Wand."
				},
				0
			)
		end

		if npc.mapTitle == "Prophet Void" then
			player:dialogSeq(
				{
					t,
					"Ah, pikiranmu cukup jernih untuk mengikuti apa yang dikatakan Wand. Itu bagus.",
					"Memahami kehampaan, ketiadaan segala sesuatu, adalah bagian terpenting menjadi mage Nagnang.",
					"Yang paling memahami Kehampaan adalah orang mati. Berjalanlah di antara kuburan di Crypt Pemakaman dan petiklah pengetahuan mereka.",
					"Setelah itu kembalilah ke Wand dengan apa pun yang diminta yang lain, dan kau akan diganjar batu itu."
				},
				0
			)
		end
	end)
}
