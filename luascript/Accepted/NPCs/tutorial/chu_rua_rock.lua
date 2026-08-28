ChuRuaRockNpc = {
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

		if (speech == "halo") then
			Tools.checkKarma(player)

			player:dialogSeq(
				{
					t,
					"Oh, pasti enak punya kaki.",
					"Kau pasti baru dari laut, tercium dari baumu.",
					"Di situlah aku hidup begitu lama sampai sekarang; di tepi laut.",
					"Terima kasih sudah menyempatkan diri bersama jiwa tua ini. Hati-hati dengan harimau di utara.",
					"Yang ia pikirkan hanya makanan, meski mungkin kau bisa mengalihkan perhatiannya kalau kau menyinggung salah satu kelinci yang mengelabuinya"
				},
				1
			)
		end
	end)
}
