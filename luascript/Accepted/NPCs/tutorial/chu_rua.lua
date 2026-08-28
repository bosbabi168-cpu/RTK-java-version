ChuRuaNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local item = Item("sea_ring")
		local tring = {graphic = item.icon, color = item.iconC}

		if player:hasLegend("aided_chu_rua") then
			player:dialogSeq(
				{
					t,
					"Sekali lagi terima kasih atas bantuanmu! Sekarang akan kupulangkan kau."
				},
				1
			)

			if player.country == 1 then
				-- kug
				player:warp(36, 7, 6)
			else
				player:warp(351, 8, 8)
			end
			return
		end

		if (player:hasItem("young_ginseng", 1) == true and not player:hasLegend("aided_chu_rua")) then
			player:dialogSeq(
				{
					t,
					"Ginseng. Akar yang bentuknya ganjil.",
					"Raja Naga akan hidup. Berkah bagimu, orang baik."
				},
				1
			)

			player:giveXP(400)

			if player.quest["tutorial_quest"] == 7 then
				-- came from ironheart/jadespear
				player:giveXP(200)
			end

			player:addKarma(1)
			player:removeItem("young_ginseng", 1, 9)
			player:addItem("sea_ring", 1)

			player:addLegend(
				"Aided Chu Rua (" .. curT() .. ")",
				"aided_chu_rua",
				5,
				128
			)

			player:dialogSeq(
				{
					tring,
					"Dengan rendah hati kupersembahkan salah satu permata terindah dari laut."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Sekali lagi terima kasih atas bantuanmu! Sekarang akan kupulangkan kau."
				},
				1
			)

			if player.country == 1 then
				-- kug
				player:warp(36, 7, 6)
			else
				player:warp(351, 8, 8)
			end

			return
		end

		player:dialogSeq(
			{
				t,
				"Aku berenang sekuat tenaga. Hei! hei kau, manusia yang terhormat. Sebentar saja! Aku ingin kau mendengar satu permohonan yang sungguh-sungguh.",
				"Sang Tuan, Raja Naga, sedang sekarat saat kita bicara, di istananya di bawah ombak. Tabib terbaik sudah datang dan menyatakan bahwa ia membutuhkan benda yang tidak bisa kami dapatkan dari dalam laut.",
				"Aku memohon kepadamu sebagai hamba rendah Raja Naga, satu-satunya hamba yang mengenal daratan dan lautan.",
				"Kumohon, kesehatan Yang Mulia bergantung pada sebatang akar Young ginseng."
			},
			1
		)

		player:dialogSeq(
			{
				tring,
				"Berikan itu kepadaku, dan sebagai gantinya cincin Putri Duyung ini akan kuserahkan kepadamu."
			},
			1
		)

		player:dialogSeq(
			{
				t,
				"Aku... andai aku bisa menunjukkan jalan ke ginseng itu, tetapi aku tidak tahu di mana ia tumbuh. Ada sebuah syair kuno,",
				"'Melompatlah ke utara, sampai kau temukan kelinci mengunyah rumput; itulah jalan menuju kesehatan dan keselarasan seorang raja,'",
				"Yang bisa kuberitahu, kau boleh menyapa beberapa binatang bersihir di daratan. Apa kata kaummu, \"Halo\"?",
				"Tolong ambilkan young ginseng demi Yang Mulia!"
			},
			0
		)
	end),

	move = function(npc, owner)
		npc_ai.moveInPlace(npc, owner)
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

		if speech == "humm dee do dum do hee" then
			Tools.checkKarma(player)

			if player:hasLegend("lost_legend") then
				player:dialogSeq(
					{
						t,
						"Kau sudah menemukan rahasia Lost Legend."
					},
					0
				)
				return
			end

			if player.quest["wind_armor"] == 0 or player.quest["min_song_asked"] == 0 then
				player:dialogSeq(
					{t, "Aku sungguh tidak paham apa yang kau bicarakan."},
					0
				)
				return
			end

			if player.quest["chu_rua_song"] == 0 then
				player.quest["chu_rua_song"] = math.random(1, 2)

				-- randomly decide between version of song
			end

			player:dialogSeq(
				{
					t,
					"Lagu para duyung, dari mana kau mendengarnya?",
					"Bukankah itu lagu yang indah?",
					"Tetapi kenapa kau tidak menyanyikan liriknya? Kau tidak tahu?",
					"Liriknya sederhana",
					"Oh ombak di atas laut, laut hijau nan purba,",
					"Mereka meluncur dan menari ke pantai, pantai keemasan,"
				},
				1
			)

			if player.quest["chu_rua_song"] == 1 then
				player:dialogSeq(
					{t, "Tarian ombak pun berakhir, kisah yang diceritakan ulang,"},
					1
				)
			elseif player.quest["chu_rua_song"] == 2 then
				player:dialogSeq(
					{t, "Tarian ombak pun berakhir, kisah pun diceritakan ulang,"},
					1
				)
			end

			player:dialogSeq(
				{
					t,
					"Jangan cemaskan tariannya, ombak itu terlalu berani,",
					"Jauh di sana nadanya bermula, di laut hijau nan purba."
				},
				0
			)
		end
	end)
}
