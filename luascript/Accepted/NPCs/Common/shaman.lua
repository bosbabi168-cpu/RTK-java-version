ShamanNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		local choice

		if player.state == 1 then
			choice = player:menuString(
				"Ah, satu lagi yang gugur datang meminta bantuanku. Siapkah kau kembali ke dunia orang hidup?",
				{"Ya", "Tidak"},
				{}
			)
		end

		if choice == "Ya" then
			player.state = 0
			player.health = player.maxHealth
			player.magic = player.maxMagic
			player:sendStatus()
			player:updateState()
			player:menuString(
				"Jadilah demikian! Jagalah dirimu tetap aman dan jauh dari bahaya.",
				{},
				{}
			)
		end
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

		if npc.mapTitle == "Dusk Shaman" and speech == "majhum" then
			Tools.checkKarma(player)

			if player.quest["valley_farm_ghost_clicked"] == 0 then
				return
			end

			player.quest["valley_farm_ghost_can_hear"] = 1
			player:dialogSeq(
				{
					t,
					"Majhum? Kau mengenalnya? Aku rindu sepupuku itu.",
					"Oh tidak! Dia meninggal? Ia orang yang begitu setia, tak sekali pun mempertanyakan perintahnya, dan bertahan di sana begitu lama.",
					"Andai aku bisa menengoknya; aku ingin berbicara dengannya sekali lagi.",
					"Oh? Kau tidak bisa mendengarnya? Hanya orang yang terbiasa berbicara dengan yang mati bisa mendengar ucapannya.",
					"Setiap jiwa punya... punya... entah bagaimana menjelaskannya, tetapi kalau kau mengetahuinya, kau akan bisa berbicara dengannya.",
					"Sebenarnya, kurasa aku bisa menunjukkan caranya berbicara dengannya, sebab aku sangat mengenal jiwanya.",
					"Coba kulihat...",
					"((Perempuan tua itu menangkupkan tangannya di telingamu; tidak ada yang terjadi sampai kau mendengar bunyi POP yang keras))",
					"Nah, sekarang kau semestinya bisa berbicara dengannya. Sampaikan salamku, aku sungguh merindukannya."
				},
				0
			)
		end

		if npc.mapTitle == "Storm Shaman" then
			if speech == "kehidupan liar" and player.quest["forgotten_path"] == 2 or player.quest[
				"forgotten_path"
			] == 3 then
				player:dialogSeq(
					{
						t,
						"Bah, aku benci belantara.\n\nAku lebih suka tinggal di sini dan menolong orang.",
						"Di luar sana tidak pernah ada orang yang bisa kutolong.",
						"Satu-satunya alasan aku dulu tinggal di belantara adalah karena aku berguru pada seorang Geomancer."
					},
					1
				)
				player.quest["forgotten_path"] = 4
			end

			if speech == "geomancer" and player.quest["forgotten_path"] == 4 then
				player:dialogSeq(
					{
						t,
						"Ya, aku berguru pada seorang Geomancer... ingatanku mengkhianatiku, kurasa namanya Rotah.",
						"Tapi kenapa kau menanyakan masa laluku?"
					},
					1
				)
				player.quest["forgotten_path"] = 5
			end
			if speech == "bola elemen" and player.quest["forgotten_path"] == 5 then
				player:dialogSeq(
					{
						t,
						"Apa katamu!?",
						"Apakah kau berhasil menempa unsur logam menjadi sebuah bola?",
						"Kalau kau bisa menemukan cara menempa unsur logam menjadi bola, aku yakin si tua Rotah itu akan memberitahumu cara menempa yang lain.",
						"Ia tidak pernah mau memberitahuku; itu sebabnya aku kembali ke sini!",
						"Oahh, dan sebaiknya kau minta bantuan pandai besi."
					},
					1
				)
				player.quest["forgotten_path"] = 6
			end
		end
	end)
}
