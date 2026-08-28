AssistantYanNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		player:dialogSeq({t, "H-h-halo? A-a-ada yang bisa kubantu?"}, 1)
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
		if player.quest["reeves_quest"] >= 3 then
			if player.quest["reeves_quest"] == 3 then
				player.quest["reeves_quest"] = 4
			end
			if speech == "malapetaka itu" then
				player:dialogSeq(
					{
						t,
						"B-b-buku itu akan jadi kematianku! Berjam-jam kuteliti bersama Sp-p-poon beberapa malam terakhir. Yang k-k-kami temukan dalam buku ini m-m-meramalkan akhir d-d-dunia di tangan i-i-iblis bernama 'Calamity'. Setelah m-m-membacanya ia menghilang dalam k-k-kepulan a-a-asap sambil berteriak 'MYTHIC'."
					},
					0
				)
			end
		end
	end)
}
