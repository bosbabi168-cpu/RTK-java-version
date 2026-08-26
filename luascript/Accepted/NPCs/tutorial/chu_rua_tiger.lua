ChuRuaTigerNpc = {
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

		local opts = {"Guild Prajurit", "Hutan", "Kota", "Guild Penyihir"}

		if speech == "halo" then
			Tools.checkKarma(player)

			npc:talk(2, "Halo, Makan Malam!")
		end

		if speech == "ginseng" then
			Tools.checkKarma(player)

			npc:talk(2, "Aku lebih suka memakanmu!")
		end

		if (speech == "kelinci") then
			Tools.checkKarma(player)

			player:dialogSeq(
				{
					t,
					"Apa? Kelinci? Jadi makhluk berbulu peloncat busuk itu yang menjebakku di lubang?"
				},
				1
			)

			local choice = player:menuSeq(
				"Aku ingin sekali mencabik lehernya. Di mana kau melihatnya?",
				opts,
				{}
			)

			if (choice == 1 or choice == 3) then
				player:dialogSeq(
					{
						t,
						"Sejauh itu? Yah, kurasa aku ngemil dulu... dan kau kelihatan lezat!"
					},
					1
				)
			elseif (choice == 2) then
				-- correct choice
				player:dialogSeq(
					{
						t,
						"Mmm. Kalau begitu akan kubalas budinya dengan gigi menyeringai."
					},
					1
				)
				player:sendMinitext("Harimau itu pergi ke selatan.")
				player:warp(1117, player.x, player.y)

				--npc:delete() -- need to work on this bit, need tiger to disappear but then respawn when player enters map
			elseif (choice == 4) then
				player:dialogSeq(
					{
						t,
						"Apa, memangnya ada yang menariknya keluar dari topi?",
						"Sejauh itu? Yah, kurasa aku ngemil dulu... dan kau kelihatan lezat!"
					},
					1
				)
			end
		end
	end)
}
