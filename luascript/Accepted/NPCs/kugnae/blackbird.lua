BlackbirdNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.quest["dagger_blue_rooster"] == 2 and player.quest[
			"crow_took_silvery_acorn"
		] == 1 then
			if player.quest["crow_took_silvery_acorn2"] == 1 then
				local smallboy = {
					graphic = convertGraphic(208, "monster"),
					color = 21
				}

				if player:hasItem("stardrop", 1) ~= true then
					player:dialogSeq(
						{
							t,
							"\"Aku pernah bertanya kepada peramal tua apakah ia tahu apa yang kubutuhkan, tetapi ia hanya berkata bahwa jawabannya akan jatuh dari bintang. Kalau kau bisa menolongku, akan kukembalikan acorn itu kepadamu.\""
						},
						0
					)
					return
				end

				player:removeItem("stardrop", 1)
				player:addItem("silvered_acorn", 1)
				player.quest["crow_took_silvery_acorn"] = 0
				player.quest["crow_took_silvery_acorn2"] = 0
				player:dialogSeq(
					{
						t,
						"Mata gagak itu membelalak. \"BERIKAN ITU!\" ia mengoceh dan menyambar Stardrop darimu."
					},
					1
				)

				player:dialogSeq(
					{
						smallboy,
						"\"Terima kasih! Ibuku pasti sangat khawatir. Ini Acorn-mu kembali. Oh, jangan hiraukan gagak yang satunya, ia cuma kawan baik.\""
					},
					0
				)

				return
			end

			player.quest["crow_took_silvery_acorn2"] = 1

			player:dialogSeq(
				{
					t,
					"Gagak itu duduk memperhatikanmu mendekat. Begitu kau cukup dekat ia mengoceh, \"Tolong jangan sakiti aku!\"",
					"\"Sebenarnya aku bocah kecil yang dulu mengira dirinya lebih pintar daripada seekor Harimau,\" kata gagak itu. \"Tetapi Harimau itu ternyata roh jahat.\"",
					"\"'Jadi kau pikir kau lebih pintar dariku?'\" kata Harimau itu, lalu ia mengubahku menjadi burung ini. \"'Nah! Sekarang kau akan tetap jadi burung kusam ini sampai kau menemukan sesuatu yang cukup terang untuk membebaskanmu!'\"",
					"\"Aku terus mencari sesuatu yang cukup terang dan berharap acorn itu berhasil. Sayangnya aku masih Gagak. Aku tidak tahu apa yang kubutuhkan!\"",
					"\"Aku pernah bertanya kepada peramal tua apakah ia tahu apa yang kubutuhkan, tetapi ia hanya berkata bahwa jawabannya akan jatuh dari bintang. Kalau kau bisa menolongku, akan kukembalikan acorn itu kepadamu.\""
				},
				0
			)
		end
	end),

	move = function(npc, owner)
		local found
		local moved = true
		local oldside = npc.side
		local checkmove = math.random(0, 10)

		if (npc.retDist <= distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1 and npc.returning == false) then
			npc.returning = true
		elseif (npc.returning == true and npc.retDist > distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1) then
			npc.returning = false
		end

		if (npc.returning == true) then
			found = toStart(npc, npc.startX, npc.startY)
		else
			if (checkmove >= 3) then
				npc.side = math.random(0, 3)
				npc:sendSide()
				if (npc.side == oldside) then
					moved = npc:move()
				end
			end
		end

		if (found == true) then
			npc.returning = false
		end
	end
}
