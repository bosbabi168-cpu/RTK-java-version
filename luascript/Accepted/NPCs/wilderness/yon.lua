local _waypointId = "yon"

YonNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local opts = {
			"Beli",
			"Jual",
			"Keahlian Kerajinan",
			"Nikmatnya Menenun",
			"Weaving Specialization"
		}

		if player.quest["wind_armor"] ~= 0 then
			table.insert(opts, "Weave Magical Net")
		end

		if (not Waypoint.isEnabled(player, _waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)

		if choice == "Beli" then
			local items = YonNpc.buyItems(npc)
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				items
			)
		elseif choice == "Jual" then
			local items = YonNpc.sellItems(npc)
			player:sellExtend("What are you willing to sell today?", items)
		elseif choice == "Keahlian Kerajinan" then
			generalNPC.crafting_skills(player, npc)
		elseif choice == "Nikmatnya Menenun" then
			YonNpc.joy_of_weaving(player, npc)
		elseif choice == "Weaving Specialization" then
			YonNpc.weaving_specialization(player, npc)
		elseif choice == "Weave Magical Net" then
			YonNpc.weaveMagicalNet(player, npc)
		elseif choice == "Waypoint" then
			Waypoint.add(player, npc, _waypointId)
		end
	end),

	buyItems = function(npc)
		local items = {}

		-- verified on NTK (what do you sell) responded with "I don't sell anything."
		return items
	end,

	sellItems = function(npc)
		local sellItems = {"cloth", "wool", "weaving_tools"}

		-- verified on NTK she only buys Weaving tools, Wool, and Cloth. (what do you buy)
		return sellItems
	end,

	joy_of_weaving = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		player:dialogSeq(
			{
				"Dengan senang hati kuceritakan soal menenun! Menenun butuh tiga hal: tangan yang mantap, wol, dan alat tenun yang baik.",
				"Wol bisa kau dapat dari domba.\nKau harus menemui tukang kayu untuk memperoleh alat tenunmu sendiri, tetapi sisanya bisa kupinjamkan. Soal tangan yang mantap, itu datang dari latihan.",
				"Katakan saja 'tenun' padaku kalau kau sudah siap mencobanya!"
			},
			0
		)
	end,

	weaving_specialization = function(player, npc)
		Tools.configureDialog(player, npc)

		if crafting.checkSpecializationLegend(player, "weaving") then
			player:dialogSeq({"Kau sudah mendalami Weaving."}, 0)
			return
		end

		crafting.checkSpecialization(player, npc, "smelting")
		crafting.checkSpecialization(player, npc, "gemcutting")

		player:dialogSeq({"Penenun bisa membuat kain dari wol. Kau mau mendalami tenun? ((Kau harus mendalaminya untuk bisa melampaui tingkat 'Accomplished'.))"}, 1)

		crafting.addSpecialization(player, npc, "weaving")
	end,

	weaveMagicalNet = function(player, npc)
		Tools.configureDialog(player, npc)
		local fineClothDialog = {graphic = convertGraphic(1633, "item"), color = 0}

		local chance = math.random(1, 10)

		if player:hasItem("fine_cloth", 10) ~= true or player:hasItem("red_potion", 1) ~= true or player:hasItem(
			"fine_weaving_tools",
			1
		) ~= true then
			player:dialogSeq({"Kau tidak punya bahan yang diperlukan untuk menenun jaring bersihir. Kau butuh (10) Fine cloth, (1) Red potion, (1) Fine weaving tools"}, 0)
		end

		if chance == 1 then
			if player.quest["magical_net_created"] == 1 then
				player:dialogSeq({"Kau sudah pernah membuat jaring bersihir."}, 0)
				return
			end

			player:removeItem("fine_cloth", 10, 9)
			player:removeItem("red_potion", 1, 9)
			player:addItem("magical_net", 1)
			player:addKarma(1)
			player.quest["magical_net_created"] = 1
			player:dialogSeq({fineClothDialog, "Kau berhasil."}, 0)
		else
			player:removeItem("fine_cloth", 10, 9)
			player:removeItem("red_potion", 1, 9)
			player:dialogSeq({"Kau gagal dalam tugas yang sangat sulit ini."}, 0)
		end
	end,

	onSayClick = async(function(player, npc)
		local yonDialog = Tools.configureDialog(player, npc)
		local speech = string.lower(player.speech)

		if speech == "tenun" then
			Tools.checkKarma(player)

			local item = Item("wool")
			local woolDialog = {graphic = item.icon, color = item.iconC}

			local item2 = Item("weaving_tools")
			local weavingToolsDialog = {graphic = item2.icon, color = item2.iconC}

			if player.quest["tutorial_quest"] == 13 then
				if player.quest["visited_yon_and_weaved"] == 1 then
					player:dialogSeq(
						{
							yonDialog,
							"Kembalilah ke tutormu dengan membawa kainnya."
						},
						1
					)

					return
				end

				player:dialogSeq(
					{
						woolDialog,
						"Jadi kau ingin membuat kain untuk Student cap-mu? Aku bisa membantumu."
					},
					1
				)

				player:dialogSeq(
					{
						weavingToolsDialog,
						"Biasanya aku tidak mengizinkan orang menenun tanpa alat sendiri, tetapi karena kau baru di tanah ini, kau boleh memakai alatku."
					},
					1
				)

				if player.quest["given_yons_weaving_tools"] == 0 then
					player:addItem("weaving_tools", 1)
					player.quest["given_yons_weaving_tools"] = 1
					return
				end

				if player:hasItem("wool", 1) ~= true then
					player:dialogSeq(
						{
							woolDialog,
							"Tapi sebelum sampai ke sana... kau butuh wol! Kembalilah ke tengah Wilderness dan kumpulkan wol.",
							"Kalau sudah terkumpul, temui aku lagi."
						},
						1
					)
					return
				elseif player:hasItem("wool", 1) == true and player:hasItem("weaving_tools", 1) == true then
					-- has wool & weaving tools
					player:removeItem("wool", 1)
					player:removeItem("weaving_tools", 1)
					player:addItem("cloth", 1)
					player.quest["visited_yon_and_weaved"] = 1
					player:dialogSeq(
						{
							yonDialog,
							"Kau menghabiskan beberapa saat dengan alat tenun Yon dan membuat sehelai kain. Alat tenun pinjaman itu kau kembalikan."
						},
						1
					)
					return
				elseif player:hasItem("weaving_tools", 1) ~= true then
					player:dialogSeq(
						{
							yonDialog,
							"Apa yang terjadi dengan sepasang alat tenun yang kupinjamkan? Kau sadar aku membutuhkannya kembali, kan?"
						},
						1
					)
					return
				end

				return
			end

			crafting.craftingDialog(player, npc, speech)

			return
		end

		if speech == "benang" then
			Tools.checkKarma(player)

			if player.quest["wool_twine"] == 1 then
				player:dialogSeq({"Ah, itu wolnya. Berikan padaku dan akan segera kupintal jadi tali untukmu."}, 1)

				if player:hasItem("wool", 10) ~= true then
					player:dialogSeq({"Mana wolnya? Tanpa 10 Wool aku tidak bisa membuatkanmu tali."}, 0)
					return
				end

				player:removeItem("wool", 10)
				player:addItem("wool_twine", 1)
				player:dialogSeq({"Ini dia, segulung tali milikmu. Semoga itu cukup, tetapi kalau kau mau lagi kau selalu bisa kembali kepadaku."}, 0)

				return
			end

			if player.quest["wool_twine"] == 0 then
				player.quest["wool_twine"] = 1
				player:dialogSeq(
					{
						"Tali wol? Ya, sangat mudah dibuat. Cukup pilin dan tarik wolnya sebentar, tetapi jangan seperti kalau kau hendak membuat benang.",
						"Kalau kau punya 10 Wool, aku bisa cepat membuatkanmu tali."
					},
					0
				)
			end

			return
		end

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, _waypointId)) then
			Waypoint.add(player, npc, _waypointId)
			return
		end
	end)
}
