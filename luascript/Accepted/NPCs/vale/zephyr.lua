local _waypointId = "zephyr"

ZephyrNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)
		local opts = {}

		if player.baseClass == 3 or player.baseClass == 4 then
			table.insert(opts, "Scribe Devotion")
			table.insert(opts, "Alchemy Devotion")
			table.insert(opts, "Scribe")
			table.insert(opts, "Alchemy")
		end

		if player.level >= 96 and player:hasLegend("understood_the_moon") and not player:hasLegend("captured_the_wind") then
			table.insert(opts, "Wind")
		end

		table.insert(opts, "Armor of the Winds")

		if (not Waypoint.isEnabled(player, _waypointId)) then
			table.insert(opts, "Waypoint")
		end

		if (Config.bossDropSalesEnabled) then
			table.insert(opts, "Jual")
		end

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Jual" then
			ZephyrNpc.sellItems(player)
		elseif menu == "Waypoint" then
			Waypoint.add(player, npc, _waypointId)
		elseif menu == "Wind" then
			player:dialogSeq(
				{
					"Selamat datang, selamat datang di tempat tertinggi di Nexus yang bisa dijejak manusia fana. Tidak jauh dari sini angin itu sendiri bermula.",
					"Memang, legendanya benar. Jiwa yang murni bisa menangkap sebagian sari angin dan memadukannya dengan kain menjadi busana bersihir.",
					"Tapi jangan keliru. Zirah yang dibentuk dari angin bukan yang paling melindungi. Meski begitu, ia lambang pencapaian dan busana yang sangat tangguh dalam pertempuran."
				},
				1
			)

			if player:karmaCheck("spirit") ~= true then
				player:dialogSeq({"Kembalilah kepadaku kalau jiwamu lebih mulia."}, 0)
				return
			end

			player.quest["wind_armor"] = 1

			player:dialogSeq(
				{
					"Jiwamu mulia. Mungkin kau akan menang atas angin yang cerdik itu. Tetapi lebih dulu kau harus menemukannya.",
					"Kudengar Legends memuat kisah seseorang lain yang berhasil. Mungkin dari Legenda itu kau menemukan jawaban yang kau cari.",
					"Ingatlah, tidak seluruh legendanya sudah ditemukan, dan tidak akan pernah. Benda sekuat itu tidak boleh jatuh ke tangan kejahatan.",
					"Kau harus menemukan orang yang tahu tentang legenda yang hilang itu kalau kau berharap bisa mengenakan zirah angin!"
				},
				0
			)
		elseif menu == "Armor of the Winds" then
			ZephyrNpc.armorOfTheWinds(player, npc)
		elseif menu == "Scribe Devotion" then
			ZephyrNpc.scribeDevotion(player, npc)
		elseif menu == "Alchemy Devotion" then
			ZephyrNpc.alchemyDevotion(player, npc)
		elseif menu == "Scribe" then
			ZephyrNpc.scribe(player, npc)
		elseif menu == "Alchemy" then
			ZephyrNpc.alchemy(player, npc)
		end
	end),

	armorOfTheWinds = function(player, npc)
		Tools.configureDialog(player, npc)
		Tools.checkKarma(player)

		if player:hasLegend("captured_the_wind") then
			player:dialogSeq({"Salam, pelayan Angin!"}, 0)
			return
		end

		if player.quest["wind_armor"] == 0 or player.quest["min_kawlana"] == 0 or not player:karmaCheck("spirit") then
			player:dialogSeq({"Aku sungguh tidak paham apa yang kau bicarakan."}, 0)
			return
		end

		local choice = player:dialogSeq(
			{
				"Jadi kau merasa siap menghadapi angin?",
				"Ini tidak semudah yang kau kira. Makhluk yang hendak kau tangkap bukan lawan yang mudah.",
				"Kau kuizinkan lewat, dan kudoakan kau beruntung."
			},
			1
		)

		if choice == true then
			-- player clicked next
			player.quest["kawlana_used"] = 0
			player.quest["kawlana_dropped"] = 0
			player:warp(1457, 8, 7)
			player:sendMinitext("Kawlana adalah sumber kekuatanmu.")
		end
	end,

	scribeDevotion = function(player, npc)
		Tools.configureDialog(player, npc)

		if (player.level < 25) then
			player:dialogSeq({"Kau belum siap menekuni satu kerajinan. Kembalilah nanti."}, 0)
			return
		end

		if crafting.checkSkillLegend(player, "scribing") then
			player:dialogSeq({"Kau sudah menekuni ilmu Scribing."}, 0)
			return
		end

		crafting.checkMentalSkill(player, npc, "potion making")

		player:dialogSeq({"Juru tulis bisa membuat gulungan bersihir yang bisa dipakai siapa saja. Biasanya gulungan itu bertuliskan ritual pertahanan."}, 1)

		crafting.addMentalSkill(player, npc, "scribing")
	end,

	alchemyDevotion = function(player, npc)
		Tools.configureDialog(player, npc)

		if (player.level < 25) then
			player:dialogSeq({"Kau belum siap menekuni satu kerajinan. Kembalilah nanti."}, 0)
			return
		end

		if crafting.checkSkillLegend(player, "potion making") then
			player:dialogSeq({"Kau sudah menekuni ilmu peramuan."}, 0)
			return
		end

		crafting.checkMentalSkill(player, npc, "scribing")

		player:dialogSeq({"Ahli ramuan bisa membuat racun untuk panah sumpit dan beberapa ramuan dengan khasiat khas."}, 1)

		crafting.addMentalSkill(player, npc, "potion making")
	end,

	scribe = function(player, npc)
		crafting.craftingDialog(player, npc, "scribe")
	end,

	alchemy = function(player, npc)
		crafting.craftingDialog(player, npc, "alchemy")
	end,

	sellItems = function(player)
		local items = {"scribes_pen", "scribes_book", "purified_water"}
		local prices = {}

		for i = 1, #items do
			table.insert(prices, math.floor(Item(items[i]).sell * 1.2))
		end

		player:sellExtend(
			"What are you willing to sell today?",
			items,
			prices
		)
	end,

	onSayClick = async(function(player, npc)
		Tools.configureDialog(player, npc)
		local speech = string.lower(player.speech)

		if speech == "tangkap angin" then
			ZephyrNpc.armorOfTheWinds(player, npc)
		end

		if speech == "juru tulis" or speech == "alkimia" then
			crafting.craftingDialog(player, npc, speech)
		end

		if speech == "peta" or speech == "pecahan" or speech == "pecahan peta" then
			if player:hasItem("map_fragment", 1) == true and player.quest["instance"] == 0 then
				player.quest["instance"] = 1
				player:dialogSeq(
					{
						"Kenapa kau selalu mendatangiku setiap menemukan dokumen baru?",
						"Apa ini? Tidak seperti apa pun yang pernah kulihat.",
						"Kusarankan kau mencari sejarawan lain yang mungkin punya lebih banyak keterangan tentang ini."
					},
					1
				)
			end
			if player.quest["instance"] == 1 then
				player:dialogSeq({"Apakah kau menemukan orang yang punya keterangannya?"}, 1)
			end
		end

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, _waypointId)) then
			Waypoint.add(player, npc, _waypointId)
			return
		end
	end)
}
