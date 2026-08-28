GanNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local options = {"Beli", "Jual"}

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			options
		)

		if choice == "Beli" then
			GanNpc.buy(player, npc)
		elseif choice == "Jual" then
			GanNpc.sell(player, npc)
		end
	end),

	buy = function(player, npc)
		local items = GanNpc.buyItems()
		player:buyExtend(
			"I think I can accomodate some of the things you need. What would you like?",
			items
		)
	end,

	sell = function(player, npc)
		local items = GanNpc.sellItems()
		player:sellExtend("What are you willing to sell today?", items)
	end,

	buyItems = function()
		local buyItems = {"short_bow", "long_spear"}
		return buyItems
	end,

	sellItems = function()
		local sellItems = {
			"short_bow",
			"long_spear",
			"iron_sword",
			"fox_blade",
			"viper_stick",
			"giasomo_stick",
		}

		if (Config.bossDropSalesEnabled) then
			table.insert(sellItems, "hoof_sabre")
			table.insert(sellItems, "might_spear")
			table.insert(sellItems, "military_fork")
			table.insert(sellItems, "jolt_trident")
			table.insert(sellItems, "maxcaliber")
			table.insert(sellItems, "moonblade")
			table.insert(sellItems, "deaths_head")
			table.insert(sellItems, "wicked_staff")
			table.insert(sellItems, "electra")
			table.insert(sellItems, "steelthorn")
			table.insert(sellItems, "titanium_lance")
			table.insert(sellItems, "star_staff")
			table.insert(sellItems, "bekyuns_spear")
			table.insert(sellItems, "hunangs_axe")
			table.insert(sellItems, "spike")
			table.insert(sellItems, "blood")
			table.insert(sellItems, "charm")
			table.insert(sellItems, "surge")
			table.insert(sellItems, "mythic_sabre")
			table.insert(sellItems, "light_sword")
			table.insert(sellItems, "dark_dagger")
		end

		return sellItems
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

		if speech == "ambil baju zirah" then
			Tools.checkKarma(player)

			if player.quest["wind_armor"] == 0 or player.quest["frost_sabre_for_seal"] ~= 2 then
				player:dialogSeq(
					{
						t,
						"Ehh... aku sungguh tidak paham apa yang kau bicarakan."
					},
					0
				)
				return
			end

			if player.quest["gan_metal"] == 0 then
				player.quest["gan_metal"] = 1

				player:dialogSeq(
					{
						t,
						"Kau datang mengambil zirah KaMing? Kenapa ia mengutus - maafkan ungkapanku - orang kota untuk mengambilnya?",
						"Ahhh... tunggu... kurasa aku tahu. Hanya lewat kau ia bisa mengambilnya tanpa menimbulkan curiga!",
						"Benar, kan? Hah? Sudah kuduga! Aku mungkin bukan bilah paling tajam, tetapi aku tahu cara menyayat pergelangan musuh.",
						"Yah, zirahnya hampir siap diambil, tetapi aku masih butuh beberapa hal.",
						"Yang pertama kubutuhkan adalah 4 logam terbaik di negeri ini. Ambilkan untukku dan cepat kembali."
					},
					0
				)
				return
			elseif player.quest["gan_metal"] == 1 then
				if player:hasItem("fine_metal", 4) ~= true then
					player:dialogSeq(
						{
							t,
							"Aku masih menunggu 4 fine metal itu untuk merampungkan perbaikan zirah KaMing."
						},
						0
					)
					return
				end

				player:removeItem("fine_metal", 4, 9)
				player.quest["gan_metal"] = 2
				player:dialogSeq(
					{
						t,
						"Terima kasih logamnya. Itu akan memperbaiki bagian ini. Tapi aku masih butuh beberapa hal lagi.",
						"Sihir dalam zirah ini mulai aus, dan daya sembuhnya tidak sekuat seharusnya.",
						"Bawakan aku sesuatu untuk mengisi kembali tenaganya.",
						"Pastikan benda itu punya daya menyembuhkan!"
					},
					0
				)
				return
			elseif player.quest["gan_metal"] == 2 then
				if player:hasItem("titanium_lance", 1) ~= true then
					player:dialogSeq(
						{
							t,
							"Aku masih butuh titanium lance untuk zirah KaMing."
						},
						0
					)
					return
				end

				player:removeItem("titanium_lance", 1)
				player.quest["gan_metal"] = 3
				player:dialogSeq(
					{
						t,
						"Ahhh.... Titanium lance ini bagus. Daya sembuhnya hebat, dan tidak terlalu kuat sehingga masih bisa kutanamkan ke zirahnya.",
						"Menanamkannya ke zirah akan makan waktu.",
						"Selagi aku mengerjakan ini, pergilah ke persembunyian Sonhi di Kugnae dan ambil beberapa tali kulit.",
						"Pergilah cepat dan ambil. Itu semestinya yang terakhir kubutuhkan."
					},
					0
				)
				return
			elseif player.quest["gan_metal"] == 3 then
				player:dialogSeq(
					{
						t,
						"Kenapa kau masih di sini? Pergilah... kau tahu di mana persembunyian Sonhi... kan?",
						"OH! Aku tahu... maaf... sudah kubilang lama-lama aku bisa memikirkan hal-hal begini.",
						"Kau tidak akan bisa masuk ke persembunyian itu sebagai orang kota. Tidak punya surat jalan dari KaMing?",
						"Kurasa untuk sekadar mengambil Zirah kau memang tidak butuh, tetapi sebaiknya kau kembali dan minta satu dari KaMing.",
						"Oh... tapi aku yakin kalau KaMing menyuruh mengambil zirah itu, ia membutuhkannya segera... sedangkan tanpa surat jalan kau tidak akan pernah bisa masuk.",
						"Begini saja... dan rahasiakan ini, ya?",
						"KaMing meninggalkan segel di sini saat terakhir menemuiku. Kurasa aku bisa mencapkan surat jalan untukmu",
						"Baiklah... jangan bilang siapa pun aku melakukan ini, dan cepat kembali.",
						"Dan JANGAN menghilang begitu saja seperti orang sebelumnya!"
					},
					1
				)

				player.quest["gan_metal"] = 4
				player:addItem("sonhi_pass", 1)
				player:dialogSeq({t, "Pergilah sekarang, dan cepat."}, 0)
				return
			elseif player.quest["gan_metal"] == 4 then
				player:dialogSeq(
					{
						t,
						"Surat jalannya sudah kuberikan. Berangkatlah ke perkemahan KaMing; aku butuh potongan kulit itu untuk zirahnya."
					},
					0
				)
				return
			end
		end

		if speech == "gurun" then
			player:dialogSeq(
				{
					t,
					"Ahhh, kadang aku merindukan gurun.",
					"Hidup di gurun itu keras; banyak orang yang belum berpengalaman mati dalam beberapa menit pertama ketika udara benar-benar panas.",
					"Punggungku masih terasa nyeri kalau ingat semua kantong air yang harus kami panggul."
				},
				0
			)
		end

		if speech == "kantong air" then
			player:dialogSeq(
				{
					t,
					"Oh tidak, aku tidak punya sisa. Ya ampun, itu sudah lama sekali.",
					"Kau harus mencari orang yang terbiasa mengurus kulit dan kain untuk membantumu soal itu."
				},
				0
			)
		end
	end)
}
