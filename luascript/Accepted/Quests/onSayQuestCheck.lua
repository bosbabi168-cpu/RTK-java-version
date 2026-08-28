Player.onSayQuestCheck = async(function(player, speech)
	-- This file is used for quests where you need to say certain things on certain maps but no npc is present

	player.npcGraphic = 0
	player.npcColor = 0
	player.dialogType = 0
	player.lastClick = 0

	speech = string.lower(speech)

	if player.mapTitle == "Office" and player.m >= 5800 and player.m <= 5996 then
		-- Nagnag office
		local t = {
			graphic = Item("lockpick").icon,
			color = Item("lockpick").iconC
		}
		if speech == "halo" then
			player:dialogSeq(
				{
					t,
					"Kau mendengar gemerisik seseorang yang terkurung",
					"\"Halo! Bisakah kau menolongku? Aku dikurung dan tidak ada kuncinya. Bisakah kau mengambilkan pencongkel kunci?\""
				},
				0
			)
		end
		if speech == "congkel kunci" then
			if player.quest["maso_lockpick"] == 0 then
				player.quest["maso_lockpick"] = 1
				player:dialogSeq(
					{
						t,
						"\"Ah ya, pencongkel kunci. Kawan lamaku Maso mungkin bisa membantumu membuatnya. Kau mungkin bisa memakai gerbang timur untuk memutari gerbang yang tak tertembus itu.\""
					},
					0
				)
			end
		end
	end
end)
