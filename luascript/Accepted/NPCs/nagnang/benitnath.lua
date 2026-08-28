BenitnathNpc = {
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
				"Apa lagi maumu? Tidak lihat aku sudah tidak punya apa-apa lagi untuk kau curi?!",
				"Oh tunggu... siapa kau? Kau bukan bagian dari pasukan keji yang menguasai tempat ini... kan?",
				"Sedang apa kau di sini? Sebenarnya tidak penting... asalkan kau di sini untuk menjadi duri bagi \"MEREKA\".",
				"Sayangnya aku tidak bisa banyak membantu. Ada satu jalan ke istana yang kutahu, lewat gorong-gorong, tetapi kurasa kau tidak mau lewat sana."
			},
			0
		)
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

		if speech == "selokan" then
			Tools.checkKarma(player)

			player:dialogSeq(
				{
					t,
					"Gorong-gorong? Ya, kau bisa menembusnya, asal kau bisa melihat di dalam sana.",
					"Pasukan itu datang dan menyumbat setiap lubang cahaya; di dalam lebih gelap daripada tengah malam, tanpa bulan atau bintang sekalipun.",
					"Aku tidak akan masuk ke sana tanpa obor atau lentera, itu pasti!",
					"Kalau kau mau masuk ke gorong-gorong, pergilah ke sudut sana; salurannya cukup besar dan akan membawamu turun ke bawah.",
					"Semoga berhasil, tetapi ingat, aku sudah memperingatkanmu!"
				},
				0
			)
		end
	end)
}
