LostLegendChestNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		player.quest["lost_legend_chest_clicked"] = 1
		player:dialogSeq(
			{
				t,
				"Hum di do dum do hi...",
				"Oh, halo. Datang menengokku?",
				"Sudah lama tidak ada yang datang menengokku.",
				"Oh, betapa serunya dulu. Sudah lama sekali itu.",
				"Sekarang yang menemaniku hanya nada di kepalaku... yah, kalau aku punya kepala.",
				"Jadi apa yang kau cari dariku? Aku menyimpan banyak rahasia, tahu."
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

		if player:hasLegend("lost_legend") then
			return
		end

		if speech == "legenda" then
			Tools.checkKarma(player)

			player.quest["lost_legend_chest_clicked"] = 1
			player:dialogSeq(
				{
					t,
					"Ah, legenda zirah yang hilang. Sepertinya sedang digemari; banyak yang menanyakannya.",
					"Tapi aku tidak bisa begitu saja memberikannya. Ia terkunci secara sihir - dengan sebuah lagu, pula.",
					"Tanpa lagunya kau tidak akan mendapatkannya."
				},
				0
			)

			--player:dialogSeq({t,"Great song."},0)
			return
		end

		-- Mermaid song
		if player.quest["chu_rua_song"] == 0 then
			-- don't do shit if they haven't visited chu rua for the song
			return
		end

		local song = {
			"oh the waves upon the sea, the green sea of old,",
			"they glide and dance to the shore, a shore of gold,"
		}

		if player.quest["chu_rua_song"] == 1 then
			table.insert(
				song,
				"the dance of the waves does end, a story been retold,"
			)
		elseif player.quest["chu_rua_song"] == 2 then
			table.insert(
				song,
				"the dance of the waves does end, a story is retold,"
			)
		end

		table.insert(song, "do not fear for the dance, the waves are too bold,")
		table.insert(song, "far away the tune begins, on the green sea of old.")

		if speech == song[1] and player.quest["chu_rua_song_stanza"] == 0 then
			player.quest["chu_rua_song_stanza"] = 2
			player:dialogSeq({t, "Astaga! Kau tahu lagunya..."}, 0)
		elseif speech == song[2] and player.quest["chu_rua_song_stanza"] == 2 then
			player.quest["chu_rua_song_stanza"] = 3
			player:dialogSeq({t, "Lanjutkan, indah sekali!"}, 0)
		elseif speech == song[3] and player.quest["chu_rua_song_stanza"] == 3 then
			player.quest["chu_rua_song_stanza"] = 4
			player:dialogSeq({t, "Ini benar-benar merdu di telingaku."}, 0)
		elseif speech == song[4] and player.quest["chu_rua_song_stanza"] == 4 then
			player.quest["chu_rua_song_stanza"] = 5
			player:dialogSeq(
				{t, "Lebih keras! Biarkan ruangan ini penuh lagu cinta!"},
				0
			)
		elseif speech == song[5] and player.quest["chu_rua_song_stanza"] == 5 then
			player.quest["chu_rua_song_stanza"] = 0

			if not player:hasLegend("lost_legend") then
				player:addLegend(
					"Discovered lost legend (" .. curT() .. ")",
					"lost_legend",
					5,
					128
				)
				player:addItem("legend_of_the_winds_2", 1)
			end
			player:dialogSeq(
				{
					t,
					"Itu dia! Itulah lagu yang perlu kudengar, dan yang akan selalu kusenandungkan.",
					"Indah, bukan?",
					"Ah, begitu puitis, begitu indah... kalau kau memahami liriknya.",
					"Nah, seperti yang dijanjikan, ini legenda yang kau cari."
				},
				0
			)
		else
			player.quest["chu_rua_song_stanza"] = 0
			player:dialogSeq(
				{t, "Peti itu mengerut. Mungkin ada baris yang kau lewatkan?"},
				0
			)
		end
	end)
}
