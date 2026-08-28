ChuRuaRabbitNpc = {
	action = function(npc)
		npc.side = math.random(0, 3)
		npc:sendSide()
		npc:move()
	end,

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

			player:dialogSeq({t, "Hmmm..", "Apa yang kau inginkan?"}, 1)
		end

		if (speech == "harimau") then
			Tools.checkKarma(player)

			npc:talk(2, "Bodoh sekali aku pergi ke utara mencari ginseng. Ia hampir memakanku!")
		end

		if (speech == "ginseng") then
			Tools.checkKarma(player)

			player:dialogSeq(
				{
					t,
					"Betapa pahit akar itu! Rasanya seburuk pegunungan tempatnya tumbuh.",
					"Seorang sepupu licik menyuruhku menyusuri jalan kiri dan mencicipi akar lezat itu.",
					"Bodoh sekali aku masuk ke pegunungan yang mengerikan itu. Aku menyusuri anak sungai ini sampai kaki gunung yang menyeramkan itu lalu melompati jalan yang berbahaya."
				},
				1
			)
		end
	end)
}
