BorderPatrolNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.quest["leviathan"] == 0 then
			player:dialogSeq(
				{
					t,
					"Aku cuma menjalankan tugas di sini. Jaga sikapmu, dan aku tidak perlu menjalankan tugasku padamu."
				},
				0
			)
		end

		if player.quest["leviathan"] ~= 0 then
			player:dialogSeq(
				{
					t,
					"Eh? Apa itu? Maaf, Orang Asing, kami tidak membiarkan siapa pun melewati perbatasan ini.",
					"Hmmm, kau berbau Leviathan. Mungkin aku bisa pura-pura tidak lihat kalau kau menyerahkan satu kulit indah yang dijatuhkan tupai hijau itu."
				},
				1
			)
		end
	end),

	handItem = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local invItem = player:getInventoryItem(player.invSlot)

		if invItem.yname == "green_squirrel_pelt" and player.quest["leviathan"] ~= 0 then
			player:removeItem("green_squirrel_pelt", 1, 9)
			player:dialogSeq(
				{
					t,
					"Wah, terima kasih banyak! Sekarang lanjutkan jalanmu dan aku tidak mengenalmu. Oh, hati-hati dengan roh Rubah yang licik itu. Mereka suka permainan kecilnya."
				},
				1
			)
			player:warp(2542, 1, 16)
		end
	end)
}
