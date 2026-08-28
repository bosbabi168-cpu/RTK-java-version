PotionShopNpc = {
	on_spawn = function(npc)
		core.gameRegistry["red_potions_available"] = 2
	end,

	action = function(npc)
		core.gameRegistry["red_potions_available"] = 2
	end,

	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual"}

		local menu = player:menuString(
			"Halo! Apa yang ingin kau lakukan hari ini?",
			opts
		)

		local buyopts = PotionShopNpc.buyItems(npc)
		local sellopts = PotionShopNpc.sellItems(npc)
		local maxAmounts = {}

		for i = 1, #buyopts do
			if buyopts[i] == "red_potion" then
				table.insert(
					maxAmounts,
					core.gameRegistry["red_potions_available"]
				)
			else
				table.insert(maxAmounts, 0)
			end
		end

		if menu == "Beli" then
			local boughtItem = player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				buyopts,
				{},
				maxAmounts
			)

			if boughtItem == nil then
				return
			end

			if Item(boughtItem[1]).yname == "red_potion" then
				local amount = boughtItem[2]
				if core.gameRegistry["red_potions_available"] - amount < 0 then
					core.gameRegistry["red_potions_available"] = 0
				else
					core.gameRegistry["red_potions_available"] = core.gameRegistry[
						"red_potions_available"
					] - 1
				end
			end
		elseif menu == "Jual" then
			player:sellExtend("What are you willing to sell today?", sellopts)
		end
	end),
	onSayClick = async(function(player, npc, speech)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local speech = string.lower(player.speech)
		if speech == "tamu khusus" and player.m == 28 and player.quest["spy_trials"] == 9 then
			player:dialogSeq(
				{
					t,
					"Ahhhhhhh, ya! Kurasa aku tahu apa yang kau butuhkan...",
					"Ramuan ini bekerja cepat tetapi pulihnya juga cepat. Aku butuh satu Mountain Ginseng untuk merampungkannya; kebetulan kau punya?"
				},
				0
			)
			if player:hasItem("mountain_ginseng", 1) == true then
				player:removeItem("mountain_ginseng", 1)
				player:addItem("slumberquick", 1)
				player.quest["spy_trials"] = 10
				player.quest["spy_potion_timer"] = os.time()
				player:dialogSeq(
					{
						t,
						"Selipkan Slumberquick ke minuman Hwan. Begitu ia tertidur, kau hanya punya empat menit untuk membawanya ke tempat interogasi sebelum ia bangun.",
						"Ada pohon di tengah Stealth Grotto yang bisa kau pakai untuk mengikatnya. Tidak ada yang bisa mendengar teriakannya di bawah sana, heh.",
						"Oh, dan kalau kau butuh botol lagi, kembalilah ke sini dan bicara lagi soal Tamu Istimewa kita.",
						"Tapi membuatnya perlu waktu, bahan ini rewel!"
					},
					0
				)
				return
			else
				return
			end
		end
		if speech == "tamu khusus" and player.m == 28 and player.quest["spy_trials"] == 10 then
			if os.time() > player.quest["spy_potion_timer"] + 7200 then
				if player:hasItem("mountain_ginseng", 1) == true then
					player:removeItem("mountain_ginseng", 1)
					player:addItem("slumberquick", 1)
					player.quest["spy_potion_timer"] = os.time()
					player:dialogSeq(
						{
							t,
							"Selipkan Slumberquick ke minuman Hwan. Begitu ia tertidur, kau hanya punya empat menit untuk membawanya ke tempat interogasi sebelum ia bangun.",
							"Ada pohon di tengah Stealth Grotto yang bisa kau pakai untuk mengikatnya. Tidak ada yang bisa mendengar teriakannya di bawah sana, heh.",
							"Oh, dan kalau kau butuh botol lagi, kembalilah ke sini dan bicara lagi soal Tamu Istimewa kita.",
							"Tapi membuatnya perlu waktu, bahan ini rewel!"
						},
						0
					)
					return
				else
					player:dialogSeq(
						{
							t,
							"Aku butuh satu Mountain Ginseng lagi untuk ramuan ini."
						},
						0
					)
					return
				end
			else
				player:dialogSeq(
					{
						t,
						"Sayangnya aku masih bersiap membuat satu lagi. Kembalilah nanti."
					},
					0
				)
			end
		end
	end),

	buyItems = function(npc)
		local buyItems = {}
		local maxAmounts = {}
		local prices = {}

		if npc.mapTitle == "Baegil Shop" then
			if core.gameRegistry["red_potions_available"] > 0 then
				buyItems = {
					"yellow_potion",
					"blue_potion",
					"violet_potion",
					"brown_potion",
					"red_potion",
					"green_potion",
					"indigo_potion",
					"white_potion",
					"herb_pipe",
					"aged_wine",
					"moon_wine"
				}
				maxAmounts = {
					0,
					0,
					0,
					0,
					core.gameRegistry["red_potions_available"],
					0,
					0,
					0,
					0,
					0,
					0
				}
			else
				buyItems = {
					"yellow_potion",
					"blue_potion",
					"violet_potion",
					"brown_potion",
					"green_potion",
					"indigo_potion",
					"white_potion",
					"herb_pipe",
					"aged_wine",
					"moon_wine"
				}
			end
		elseif npc.mapTitle == "Bagai Shop" then
			if core.gameRegistry["red_potions_available"] > 0 then
				buyItems = {
					"yellow_potion",
					"blue_potion",
					"violet_potion",
					"brown_potion",
					"red_potion",
					"green_potion",
					"indigo_potion",
					"white_potion",
					"aged_wine",
					"thick_wine",
					"rich_wine"
				}
				maxAmounts = {
					0,
					0,
					0,
					0,
					core.gameRegistry["red_potions_available"],
					0,
					0,
					0,
					0,
					0,
					0
				}
			else
				buyItems = {
					"yellow_potion",
					"blue_potion",
					"violet_potion",
					"brown_potion",
					"green_potion",
					"indigo_potion",
					"white_potion",
					"aged_wine",
					"thick_wine",
					"rich_wine"
				}
			end
		end

		for i = 1, #buyItems do
			table.insert(prices, Item(buyItems[i]).price)
		end

		return buyItems, prices, maxAmounts
	end,

	sellItems = function(npc)
		local sellItems = PotionShopNpc.buyItems(npc)

		if core.gameRegistry["red_potions_available"] == 0 then
			table.insert(sellItems, "red_potion")
		end
		
		table.insert(sellItems, "ginseng_piece")
		table.insert(sellItems, "ginseng")
		table.insert(sellItems, "mountain_ginseng")

		return sellItems
	end
}
