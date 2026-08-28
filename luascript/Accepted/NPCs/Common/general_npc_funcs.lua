general_npc_funcs = {
	setTitle = function(player, npc)
		if player.level < 75 then
			player:dialog(
				"Kembalilah kalau kau sudah mencapai pencerahan ke-75.",
				{}
			)
		end

		local title = player:inputLetterCheck(player:inputSeq(
			"Your heart is in the right place. Which title shall you take?",
			"",
			"",
			{},
			{}
		))

		local strlen = string.len(title)

		if strlen > 12 then
			-- The string length of 12 was verified on NTK.  The only way to get a longer title (up to 16 characters) is to seek a Chongun who has Title of Nobility Spell.
			player:dialog(
				"Gelar yang kau masukkan tidak boleh lebih dari 12 huruf.",
				{}
			)
			return
		end

		local totalcost = 200 * strlen

		local confirm = player:menuSeq(
			"Untuk gelar itu, " .. Tools.formatNumber(totalcost) .. " keping diperlukan. Kau mau melakukannya?",
			{"Ya", "Tidak"},
			{}
		)

		if confirm == 1 then
			if player.money < totalcost then
				player:dialog(
					"Kau tidak punya " .. totalcost .. " emas yang diperlukan untuk menetapkan gelar ini.",
					{}
				)
				return
			end

			if player.title == title then
				player:dialog(
					"Kau hanya membuang uang kalau menetapkan gelar yang sama dua kali.",
					{}
				)
				return
			end

			player:removeGold(totalcost)
			player.title = title
			player:sendMinitext("Gelarmu telah diubah menjadi: " .. title)
			player:sendStatus()
		end
	end,

	checks = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local choiceA = player:menuSeq(
			"Salam, selamat datang di tokoku. Ada yang bisa kubantu hari ini?",
			{"Aku mau membeli cek.", "Aku mau mencairkan cek."},
			{}
		)

		local checkopts = {
			"1.000 emas.",
			"5.000 emas.",
			"10.000 emas.",
			"50.000 emas.",
			"100.000 emas.",
			"500.000 emas.",
			"1.000.000 emas."
		}
		local checkAmounts = {1000, 5000, 10000, 50000, 100000, 500000, 1000000}
		local items = {
			"check_1000",
			"check_5000",
			"check_10000",
			"check_50000",
			"check_100000",
			"check_500000",
			"check_1000000"
		}

		if choiceA == 1 then
			-- buy check

			local choiceB = player:menuSeq(
				"Ah ya.. cek senilai berapa yang kau inginkan?",
				checkopts,
				{}
			)
			if player.money < checkAmounts[choiceB] then
				player:dialogSeq(
					{
						t,
						"Kembalilah kalau emasmu cukup, atau pilih cek yang lebih kecil."
					},
					0
				)
				return
			end

			player:removeGold(checkAmounts[choiceB])
			player:addItem(items[choiceB], 1)

			player:dialogSeq(
				{
					t,
					"Ini dia. Bilang saja kalau ada lagi yang bisa kubantu."
				},
				0
			)
			return
		elseif choiceA == 2 then
			-- cash check
			local choiceB = player:menuSeq(
				"Ah ya, cek mana yang ingin kau cairkan?",
				checkopts,
				{}
			)

			if player:hasItem(items[choiceB], 1) ~= true then
				player:dialogSeq(
					{t, "Kau tidak punya cek senilai itu."},
					0
				)
				return
			end

			player:addGold(checkAmounts[choiceB])
			player:removeItem(items[choiceB], 1, 9)

			player:dialogSeq({t, "Ini uangmu, senang bisa membantu."}, 0)

			return
		end
	end,

	warPaint = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local armor = player:getEquippedItem(EQ_ARMOR)
		local coat = player:getEquippedItem(EQ_COAT)

		local color = 0

		if armor == nil and coat == nil then
			player:dialogSeq(
				{
					t,
					"Kau harus mengenakan zirah atau mantel untuk melihat cat perangmu. Kau boleh melanjutkan, tetapi warnanya belum akan terlihat sampai kau memakainya."
				},
				1
			)
		else
			if armor ~= nil then
				color = Item(armor.id).lookC

				-- returns original color of the armor from item database (for bleaching back to normal)
			end

			if coat ~= nil then
				color = Item(coat.id).lookC

				-- returns original color of the armor from item database (for bleaching back to normal)
			end
		end

		clone.wipe(player)

		if player.armorColor ~= 0 then
			local choice = player:menuSeq(
				"Kau ingin cat perangmu kuluruhkan seharga 10 emas?",
				{"Putihkan rambutku", "Tidak"},
				{}
			)

			if choice == 1 then
				if player.money < 10 then
					player:dialogSeq(
						{t, "Temui aku lagi kalau emasmu sudah cukup."},
						0
					)
					return
				end

				player:removeGold(10)
				player.armorColor = 0
				player.gfxDye = 0
				player:refresh()

				player:dialogSeq({t, "Sudah selesai."}, 0)
				return
			elseif choice == 2 then
				player:dialogSeq({t, "Sesuai keinginanmu."}, 0)
				return
			end
		elseif player.armorColor == 0 then
			-- not dyed

			if player.level == 99 then
				local dyes = {"Brown (1000 gold)"}
				local dyeCost = {1000}

				if player.baseHealth >= 50000 or player.baseMagic >= 25000 then
					table.insert(dyes, "Wasabi (5000 gold)")
					table.insert(dyeCost, 5000)
				end

				if player.baseHealth >= 160000 or player.baseMagic >= 80000 then
					table.insert(dyes, "Super Wasabi (12000 gold)")
					table.insert(dyeCost, 12000)
				end

				local choice = player:menuSeq(
					"Apakah Anda ingin mempertimbangkan pewarna istimewa, Yang Agung?",
					{
						"Ya, silakan",
						"Tidak, aku sudah cukup istimewa tanpa pewarna semacam itu."
					},
					{}
				)

				if choice == 1 then
					-- saying yes to consider special dye
					local choice2 = player:menuSeq(
						"Pewarna mana yang Anda inginkan, Yang Agung?",
						dyes,
						{}
					)

					if player.money < dyeCost[choice2] then
						player:dialogSeq(
							{
								t,
								"Kalau Anda tidak sanggup membayarnya, mungkin Anda tidak seagung itu..."
							},
							0
						)
						return
					end

					player:removeGold(dyeCost[choice2])

					if choice2 == 1 then
						-- brown dye (you could use menuString here but then you will have to type out completely "Brown (1000 gold)"
						player.armorColor = 12

						-- brown
					elseif choice2 == 2 then
						player.armorColor = 16

						-- wasabi
					elseif choice2 == 3 then
						player.armorColor = 36

						-- super wasabi
					end
					player.gfxDye = 0
					player:refresh()
					player:dialogSeq({t, "Sudah selesai."}, 0)

					return
				end
			end

			--- default dialog text for everyone, including 99 unless they selected a special dye and hit the return statement above. If they chose to not dye theemselves, then they continue into the statements below

			local choice = player:menuSeq(
				"Untuk ikut pertempuran regu kau butuh pewarna. Harganya 20 keping. Kau mau?",
				{"Ya", "Tidak"},
				{}
			)

			if choice == 1 then
				-- yes
				if player.money < 20 then
					player:dialogSeq(
						{t, "Temui aku lagi kalau emasmu sudah cukup."},
						0
					)
					return
				end

				local teams = {
					"Hyun moo",
					"Ju jak",
					"Chung ryong",
					"Baekho",
					"Ash",
					"River",
					"Fire",
					"Snow"
				}
				local colors = {10, 21, 24, 11, 28, 17, 31, 29}

				local teamChoice = player:menuSeq(
					"Regu mana yang ingin kau masuki?",
					teams,
					{}
				)

				player:removeGold(20)

				player.armorColor = colors[teamChoice]
				player.gfxDye = 0
				player:refresh()

				player:dialogSeq(
					{
						t,
						"Semoga langit menganugerahkan kematian tanpa rasa sakit.",
						"(Pastikan kau bisa bergrup dengan regumu. Tekan 'SHIFT G' agar Juaramu bisa memasukkanmu ke grup.)",
						"(Kalau kau sang Juara, tekan 'g' untuk menambah atau mengeluarkan seseorang dari grupmu.)"
					},
					0
				)
				return
			elseif choice == 2 then
				player:dialogSeq(
					{
						t,
						"Kau tidak sedang bilang 20 keping itu kemahalan, kan? Aku tidak bisa memberi lebih murah dari itu."
					},
					0
				)
				return
			end
		end
	end,

	wisdomClothes = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local items = {
			"chutze",
			"doolze",
			"setze",
			"netze",
			"dasutze",
			"yeoseutze",
			"yilgopze",
			"yeodulpze",
			"ahhopze",
			"yulze",
			"yeolhanbeunze",
			"yeoldoobeunze",
			"yeolsebeunze",
			"yeolnebeunze",
			"yeoldaseobeunze",
			"yeolyeoseobeunze",
			"yeolilgobbeunze",
			"yeolyeodeobeunze",
			"yeoahhopbeunze"
		}
		local itemType = ""

		if player.sex == 0 then
			-- male
			itemType = "robe"
		elseif player.sex == 1 then
			-- female
			itemType = "gown"
		end

		years = math.floor((os.time() - player.registry["firstTimeLogin"]) / 31557600)

		if years == 0 then
			player:dialogSeq(
				{
					t,
					"Kau belum siap memegang " .. itemType .. " kebijaksanaan. Kembalilah nanti."
				},
				0
			)
			return
		end

		--player:talk(0,""..items[years].."_"..itemType)
		local item = Item(items[years] .. "_" .. itemType)

		if item == nil then
			return
		end

		-- 1 year = ChutZe gown or robe
		-- 2 year = DoolZe gown or robe
		-- 3 year = SetZe gown or robe
		-- 4 year = NetZe gown or robe
		-- 5 year = DaSutZe gown or robe
		-- 6 year = YeoSutZe gown or robe
		-- 7 year = YilGopZe gown or robe
		-- 8 year = YeoDulpZe gown or robe
		-- 9 year = AhHopZe gown or robe
		-- 10 year = YulZe gown or robe
		-- 11 year = YeolHanBeunZe gown or robe
		-- 12 year = YeolDooBeunZe gown or robe
		-- 13 year = YeolSeBeunZe gown or robe
		-- 14 year = YeolNeBeunZe gown or robe
		-- 15 year = YeolDaseoBeunZe gown or robe
		-- 16 year = YeolYeoSeoBeunZe gown or robe
		-- 17 year = YeolIlgobBeunZe gown or robe
		-- 18 year = YeolYeodeoBeunZe gown or robe
		-- 19 year = YeoAhHopBeunZe gown or robe

		local choice = player:menuSeq(
			"Ah, Anda sudah cukup lama di sini. Berminatkah Anda pada " .. item.name .. "?",
			{"Ya", "Tidak"},
			{}
		)

		if choice == 1 then
			local subchoice = player:menuSeq(
				"Sungguh tidak enak meminta kepada orang sebijaksana Anda, yang sudah sekian lama berada di tanah kami, tetapi saya perlu 100.000 emas untuk menutup biaya bahan pembuatan " .. itemType .. " yang sehalus itu. Bersediakah Anda membayar?",
				{"Ya, bersedia", "Tidak, tidak bersedia"},
				{}
			)

			if subchoice == 1 then
				if player.money < 100000 then
					player:dialogSeq(
						{
							t,
							"Saya senang Anda bersedia membayar. Begitu emasnya ada, kembalilah dan akan saya jualkan satu."
						},
						0
					)
					return
				end

				player:removeGold(100000)
				player:addItem(item.name, 1, 0, player.ID)
				player:dialogSeq(
					{
						t,
						"Ini " .. item.name .. " Anda, kenakanlah dengan bangga."
					},
					0
				)
			elseif subchoice == 2 then
				player:dialogSeq(
					{
						t,
						"Maafkan saya, tetapi bahan jubah ini sangat mahal. Saya harus tetap meminta emasnya."
					},
					0
				)
			end
		end
	end,

	moveToCountry = function(player, npc, country)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.level < 20 then
			player:dialogSeq(
				{
					t,
					"Halo! Kau masih terlalu baru di tanah ini untuk memikirkan pindah ke kerajaan lain. Mungkin nanti kalau kau sudah siap."
				},
				0
			)
			return
		end

		if player.country ~= 0 and country ~= 0 then
			-- Aligned to kingdom already and trying to join another kingdom that is not wilderness
			player:dialogSeq(
				{
					t,
					"Aku tidak bisa mengizinkanmu pindah ke sini selama kau masih bersumpah setia pada kerajaan lain. Hanya yang netral yang bisa bergabung dengan sebuah kerajaan."
				},
				0
			)
			return
		end

		if country == 0 then
			-- Wilderness

			if player.country == 0 then
				--already neutral
				player:dialogSeq({t, "Ah, hidup bebas. Bukankah hebat?"}, 0)
				return
			end

			if player.country ~= 0 then
				player:dialogSeq(
					{
						t,
						"Selamat datang, orang kota. Bukankah di luar sini menyenangkan?",
						"Maukah kau meninggalkan kota dan menjadi bagian dari belantara?",
						"Itu berarti kau meninggalkan semua yang kau punya: klanmu, kesetiaanmu, rumahmu, dan kawan-kawanmu."
					},
					1
				)

				local subchoice = player:menuSeq(
					"Apakah kau masih ingin jadi Neutral?",
					{"Tidak, aku lebih baik tidak.", "Ya, silakan."},
					{}
				)

				if subchoice == 2 then
					player:updateCountry(0)
					player.registry["home"] = 0
					player:sendStatus()

					--player:dialogSeq({t,"Welcome to the wilderness. ((Log out and back in for this change to take effect."},0)
					player:dialogSeq({t, "Selamat datang di belantara."}, 0)
					return
				end
			end
		end

		if country == 1 then
			-- move to kugnae city

			if player.country == 1 then
				player:dialogSeq({t, "Greetings, fellow Koguryian."}, 0)
				return
			end

			local choice = player:menuSeq(
				"Maukah kau menjadi warga kota kami yang indah, Kugnae?",
				{"Tidak, terima kasih.", "Ya, sangat."},
				{}
			)

			if choice == 2 then
				if player:hasItem("gold_acorn", 20) ~= true then
					player:dialogSeq(
						{
							t,
							"Kugnae meminta 20 gold acorn sebagai upeti untuk pindah. Kembalilah kalau kau sudah punya."
						},
						0
					)
					return
				end

				player:removeItem("gold_acorn", 20)
				player:updateCountry(country)
				player.registry["home"] = 0
				player:dialogSeq({t, "Selamat datang di Kugnae."}, 0)
			end
		end

		if country == 2 then
			-- move to buya city

			if player.country == 2 then
				player:dialogSeq({t, "Greetings, fellow Buyan."}, 0)
				return
			end

			local choice = player:menuSeq(
				"Maukah kau menjadi warga kota kami yang indah, Buya?",
				{"Tidak, terima kasih.", "Ya, sangat."},
				{}
			)

			if choice == 2 then
				if player:hasItem("gold_acorn", 20) ~= true then
					player:dialogSeq(
						{
							t,
							"Buya meminta 20 gold acorn sebagai upeti untuk pindah. Kembalilah kalau kau sudah punya."
						},
						0
					)
					return
				end

				player:removeItem("gold_acorn", 20)
				player:updateCountry(country)
				player.registry["home"] = 0
				player:dialogSeq({t, "Selamat datang di Buya."}, 0)
			end
		end

		if country == 3 then
			-- move to nagnang city

			if player.country == 3 then
				player:dialogSeq({t, "Greetings, fellow Nagnang citizen."}, 0)
				return
			end

			local choice = player:menuSeq(
				"Maukah kau menjadi warga kota kami yang indah, Nagnang?",
				{"Tidak, terima kasih.", "Ya, sangat."},
				{}
			)

			if choice == 2 then
				if player:hasItem("gold_acorn", 20) ~= true then
					player:dialogSeq(
						{
							t,
							"Nagnang meminta 20 gold acorn sebagai upeti untuk pindah. Kembalilah kalau kau sudah punya."
						},
						0
					)
					return
				end

				player:removeItem("gold_acorn", 20)
				player:updateCountry(country)
				player.registry["home"] = 0
				player:dialogSeq({t, "Selamat datang di Nagnang."}, 0)
			end
		end
	end,

	broadcastEvent = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.level < 11 then
			player:dialogSeq(
				{
					t,
					"Setelah mencapai level 11, kau bisa menyiarkan pesan kepada masyarakat RetroTK tentang acara yang kau selenggarakan."
				},
				0
			)
			return
		end

		if player.registry["hostCommunityEventTimer"] > os.time() then
			player:dialogSeq(
				{
					t,
					"Kau baru saja menyiarkan pesan ke RetroTK. Tunggu satu hari."
				},
				0
			)
			return
		end

		player:dialogSeq(
			{
				t,
				"Kau boleh menyiarkan pesan tentang acara yang kau selenggarakan, sekali sehari. Biayanya 2.000 emas."
			},
			1
		)

		local choice = player:menuSeq(
			"Kau bersedia membayar 2.000 emas?",
			{"Ya", "Tidak, aku berubah pikiran."},
			{}
		)

		if choice == 1 then
			if player.money < 2000 then
				player:dialogSeq({t, "Kembalilah kalau emasmu sudah cukup."}, 0)
				return
			end

			local events = {
				"Auction",
				"Giveaway",
				"Trivia contest",
				"Acaraku cukup rumit. Sudah",
				"   kuumumkan di Community Events."
			}
			local eventLocations = {
				"North gate",
				"East gate",
				"South gate",
				"West gate",
				"Palace"
			}
			local eventLocationChoice = 0

			local place = ""

			if player.region == 0 then
				place = "Kugnae"
			elseif player.region == 1 then
				place = "Buya"
			elseif player.region == 2 then
				place = "Mythic"
			else
				place = "Mythic"
			end

			local subchoice = player:menuSeq(
				"Acara jenis apa yang kau umumkan?",
				events,
				{}
			)

			if subchoice == 1 or subchoice == 2 or subchoice == 3 then
				eventLocationChoice = player:menuSeq(
					"Di mana orang-orang harus berkumpul untuk " .. events[
						subchoice
					],
					eventLocations,
					{}
				)
				broadcast(
					-1,
					player.name .. " is hosting a " .. events[subchoice] .. " at " .. place .. " " .. eventLocations[
						eventLocationChoice
					]
				)
			elseif subchoice == 4 or subchoice == 5 then
				local gender = ""
				if player.sex == 0 then
					gender = "his"
				elseif player.sex == 1 then
					gender = "her"
				end

				broadcast(
					-1,
					player.name .. " invites all to read about " .. gender .. " event on Community events"
				)
			end

			player:removeGold(2000)
			player.registry["hostCommunityEventTimer"] = os.time() + 86400
		end
	end,

	massExchange = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		--player:dialogSeq({t,"Disabled until further notice."},0)

		local goldAmount = 0

		local id = 0
		local amount = 0
		local engrave = ""
		local time = 0
		local owner = 0

		local bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable = player:bankItemsList()

		local itemsToTrade = {}

		player:dialogSeq(
			{
				t,
				"Salam. Kemampuan ini memungkinkanmu menjual barang dalam jumlah besar langsung dari simpananmu ke simpanan orang lain.",
				"Orang yang berdagang denganmu harus berada di dekatmu, membawa uangnya, atau punya barang di simpanannya."
			},
			1
		)
		local input = player:inputSeq(
			"Who is the person you are trading with?",
			"I am trading with",
			"today.",
			{},
			{}
		)

		local tradee = Player(input)

		if tradee == nil then
			-- Online check
			player:dialogSeq(
				{
					t,
					"Maaf, aku tidak menemukan " .. input .. " di pasar. Minta dia datang ke sini untuk berdagang."
				},
				0
			)
			return
		end

		if tradee.m ~= player.m or not distanceSquare(player, tradee, 5) then
			-- Distance check
			player:dialogSeq(
				{
					t,
					"Maaf, aku tidak menemukan " .. tradee.name .. " di pasar. Minta dia datang ke sini untuk berdagang."
				},
				0
			)
			return
		end

		-- NTK sucks.... "You can't trade with an unregistered player or a player below level 11."

		if tradee.level < 11 then
			player:dialogSeq(
				{t, "Kau tidak bisa berdagang dengan pemain di bawah level 11."},
				0
			)
			return
		end

		if player.level < 11 then
			player:dialogSeq(
				{t, "Kau belum bisa berdagang sampai levelmu 11 atau lebih."},
				0
			)
			return
		end

		if player.name == tradee.name then
			player:dialogSeq({t, "Kau tidak bisa berdagang dengan dirimu sendiri."}, 0)
			return
		end

		local goldChoice = player:menuSeq(
			"Apakah kau akan menambahkan emas pada tukar-menukar ini?",
			{"Ya", "Tidak"},
			{}
		)

		if goldChoice == 1 then
			goldAmount = player:inputNumberCheck(player:inputSeq("How much gold are you adding?", "I will add", "gold.", {}, {}))

			if goldAmount > player.money then
				player:dialogSeq({t, "Emasmu tidak sebanyak itu."}, 0)
				return
			end
		end

		local itemChoice = player:menuSeq(
			"Apakah kau ingin menambahkan satu barang?",
			{"Ya", "Tidak"},
			{}
		)

		repeat

			if itemChoice == 1 then
				itemsToTrade, bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable = general_npc_funcs.massExchangeWithdraw(
					player,
					npc,
					itemsToTrade,
					bankItemTable,
					bankCountTable,
					bankOwnerTable,
					bankEngraveTable,
					bankTimerTable
				)

				itemChoice = player:menuSeq(
					"Apakah kau ingin menambahkan satu barang lagi?",
					{"Ya", "Tidak"},
					{}
				)
			elseif itemChoice == 2 then
				break
			end
		until itemChoice == 2

		local itemString = ""

		itemString = itemString .. player.name .. " added " .. Tools.formatNumber(tonumber(goldAmount)) .. " gold.\n"

		if itemsToTrade ~= nil then
			for i = 1, #itemsToTrade do
				itemString = itemString .. itemsToTrade[i].amount .. " " .. Item(itemsToTrade[i].id).name .. "\n"
			end
		end

		player:dialogSeq({t, itemString}, 1)

		local confirm = player:menuSeq("Apakah itu benar?", {"Ya", "Tidak"}, {})

		if confirm == 1 then
			player:dialogSeq({t, "Baik, biar kuambil daftar pedagang lainnya."}, 1)

			tradee:freeAsync()
			general_npc_funcs.counterTrade(
				tradee,
				player,
				npc,
				goldAmount,
				itemsToTrade
			)
		elseif confirm == 2 then
			player:dialogSeq({t, "Oh, kalau begitu sebaiknya kita mulai lagi."}, 0)
			return
		end
	end,

	counterTrade = async(function(player, trader, npc, goldOffered, itemsOffered)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local goldAmount = 0

		local id = 0
		local amount = 0
		local engrave = ""
		local time = 0
		local owner = 0

		local bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable = player:bankItemsList()

		local itemsToTrade = {}

		local choice = player:menuSeq(
			trader.name .. " ingin berdagang denganmu. Kau siap berdagang?",
			{"Ya", "Tidak"},
			{}
		)

		local itemsOfferedString = ""

		itemsOfferedString = itemsOfferedString .. trader.name .. " added " .. Tools.formatNumber(tonumber(goldOffered)) .. " gold.\n"

		if itemsOffered ~= nil then
			for i = 1, #itemsOffered do
				itemsOfferedString = itemsOfferedString .. itemsOffered[i].amount .. " " .. Item(itemsOffered[i].id).name .. "\n"
			end
		end

		player:dialogSeq({t, itemsOfferedString}, 1)

		local goldChoice = player:menuSeq(
			"Apakah kau akan menambahkan emas pada tukar-menukar ini?",
			{"Ya", "Tidak"},
			{}
		)

		if goldChoice == 1 then
			goldAmount = player:inputNumberCheck(player:inputSeq("How much gold are you adding?", "I will add", "gold.", {}, {}))

			if goldAmount > player.money then
				player:dialogSeq({t, "Emasmu tidak sebanyak itu."}, 0)
				return
			end
		end

		local itemChoice = player:menuSeq(
			"Apakah kau ingin menambahkan satu barang?",
			{"Ya", "Tidak"},
			{}
		)

		repeat

			if itemChoice == 1 then
				itemsToTrade, bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable = general_npc_funcs.massExchangeWithdraw(
					player,
					npc,
					itemsToTrade,
					bankItemTable,
					bankCountTable,
					bankOwnerTable,
					bankEngraveTable,
					bankTimerTable
				)

				itemChoice = player:menuSeq(
					"Apakah kau ingin menambahkan satu barang lagi?",
					{"Ya", "Tidak"},
					{}
				)
			elseif itemChoice == 2 then
				break
			end
		until itemChoice == 2

		local itemString = ""

		itemString = itemString .. player.name .. " added " .. Tools.formatNumber(tonumber(goldAmount)) .. " gold.\n"

		if itemsToTrade ~= nil then
			for i = 1, #itemsToTrade do
				itemString = itemString .. itemsToTrade[i].amount .. " " .. Item(itemsToTrade[i].id).name .. "\n"
			end
		end

		player:dialogSeq({t, itemString}, 1)

		player:dialogSeq(
			{t, "Baik, jadi kau akan menerima ini:\n" .. itemsOfferedString},
			1
		)
		player:dialogSeq({t, "Baik, dan kau akan memberikan ini:\n" .. itemString}, 1)

		local confirm = player:menuSeq(
			"Apakah semuanya sudah benar?",
			{"Ya", "Tidak"},
			{}
		)

		if confirm == 1 then
			trader:freeAsync()
			general_npc_funcs.finalTrade(
				trader,
				player,
				npc,
				goldAmount,
				itemsToTrade,
				goldOffered,
				itemsOffered
			)
		elseif confirm == 2 then
			player:dialogSeq({t, "Oh, kalau begitu sebaiknya mulai lagi."}, 0)
		end
	end),

	finalTrade = async(function(player, trader, npc, goldOffered, itemsOffered, goldPlayerOffered, itemsPlayerOffered)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local goldAmount = 0

		local id = 0
		local amount = 0
		local engrave = ""
		local time = 0
		local owner = 0

		local itemString = ""

		itemString = itemString .. trader.name .. " added " .. Tools.formatNumber(tonumber(goldOffered)) .. " gold.\n"

		if itemsOffered ~= nil then
			for i = 1, #itemsOffered do
				itemString = itemString .. itemsOffered[i].amount .. " " .. Item(itemsOffered[i].id).name .. "\n"
			end
		end

		local itemsPlayerOfferedString = ""

		itemsPlayerOfferedString = itemsPlayerOfferedString .. player.name .. " added " .. Tools.formatNumber(tonumber(goldPlayerOffered)) .. " gold.\n"

		if itemsPlayerOffered ~= nil then
			for i = 1, #itemsPlayerOffered do
				itemsPlayerOfferedString = itemsPlayerOfferedString .. itemsPlayerOffered[
					i
				].amount .. " " .. Item(itemsPlayerOffered[i].id).name .. "\n"
			end
		end

		player:dialogSeq(
			{t, "Pedagang lain menambahkan ini ke daftar:\n" .. itemString},
			1
		)
		player:dialogSeq(
			{
				t,
				"Sebagai gantinya kau memberikan ini:\n" .. itemsPlayerOfferedString
			},
			1
		)

		local confirm = player:menuSeq(
			"Apakah semuanya sudah benar?",
			{"Ya", "Tidak"},
			{}
		)

		if player == nil then
			return
		end
		if trader == nil then
			return
		end

		if confirm == 1 then
			player:dialogSeq({t, "Baiklah, biar kulakukan pertukarannya."}, 1)
			general_npc_funcs.completeTrade(
				player,
				trader,
				npc,
				goldOffered,
				itemsOffered,
				goldPlayerOffered,
				itemsPlayerOffered
			)
		end
	end),

	completeTrade = function(trader, tradee, npc, goldOffered, itemsOffered, goldPlayerOffered, itemsPlayerOffered)
		-- trader = original trade initiator, tradee = 2nd person in the trade
		local goldAmount = 0

		local id = 0
		local amount = 0
		local name = ""
		local realName = ""
		local time = 0
		local owner = 0

		local fraud = false

		-- check to make sure items are still present in banks --

		if itemsOffered ~= nil then
			for i = 1, #itemsOffered do
				local bankItem = tradee:retrieveBankItem(itemsOffered[i].id)

				if bankItem == nil then
					fraud = true
				end

				if bankItem[2] < itemsOffered[i].amount then
					fraud = true
				end
			end
		end

		if itemsPlayerOffered ~= nil then
			for i = 1, #itemsPlayerOffered do
				local bankItem = trader:retrieveBankItem(itemsPlayerOffered[i].id)

				if bankItem == nil then
					fraud = true
				end

				if bankItem[2] < itemsPlayerOffered[i].amount then
					fraud = true
				end
			end
		end

		---- check to make sure each individual in transaction still has the gold --
		if trader.money < goldPlayerOffered then
			fraud = true
		end

		if tradee.money < goldOffered then
			fraud = true
		end

		if fraud then
			trader:dialogSeq(
				{
					t,
					"Pertukaran massal dibatalkan karena percobaan penipuan. GM telah diberi tahu."
				},
				0
			)
			tradee:dialogSeq(
				{
					t,
					"Pertukaran massal dibatalkan karena percobaan penipuan. GM telah diberi tahu."
				},
				0
			)
			return
		end

		------------------------------------------------------

		--- handle gold --
		tradee:addGold(tonumber(goldPlayerOffered))
		tradee:sendMinitext("Kau menerima " .. goldPlayerOffered .. " emas")
		trader:removeGold(tonumber(goldPlayerOffered))
		trader:sendMinitext("Kau memberikan " .. goldPlayerOffered .. " emas")

		trader:addGold(tonumber(goldOffered))
		trader:sendMinitext("Kau menerima " .. goldOffered .. " emas")
		tradee:removeGold(tonumber(goldOffered))
		tradee:sendMinitext("Kau memberikan " .. goldOffered .. " emas")

		--- end handle gold --

		-- handle items --

		--[[trader:talk(0,"items offered from tradee")
	for i = 1, #itemsOffered do
		trader:talk(0,""..itemsOffered[i].id.." amt: "..itemsOffered[i].amount)
	end

	trader:talk(0,"items offered from trader")
	for i = 1, #itemsPlayerOffered do
		trader:talk(0,""..itemsPlayerOffered[i].id.." amt: "..itemsPlayerOffered[i].amount)
	end]]
		--

		if itemsOffered ~= nil then
			for i = 1, #itemsOffered do
				trader:bankDeposit(
					itemsOffered[i].id,
					itemsOffered[i].amount,
					itemsOffered[i].owner,
					itemsOffered[i].time,
					itemsOffered[i].realName
				)
				tradee:bankWithdraw(
					itemsOffered[i].id,
					itemsOffered[i].amount,
					itemsOffered[i].owner,
					itemsOffered[i].time,
					itemsOffered[i].realName
				)
			end
		end

		if itemsPlayerOffered ~= nil then
			for i = 1, #itemsPlayerOffered do
				trader:bankWithdraw(
					itemsPlayerOffered[i].id,
					itemsPlayerOffered[i].amount,
					itemsPlayerOffered[i].owner,
					itemsPlayerOffered[i].time,
					itemsPlayerOffered[i].realName
				)
				tradee:bankDeposit(
					itemsPlayerOffered[i].id,
					itemsPlayerOffered[i].amount,
					itemsPlayerOffered[i].owner,
					itemsPlayerOffered[i].time,
					itemsPlayerOffered[i].realName
				)
			end
		end

		characterLog.massExchangeWrite(
			trader,
			goldPlayerOffered,
			itemsPlayerOffered,
			tradee,
			goldOffered,
			itemsOffered
		)

		trader:sendMinitext("Tukar-menukar selesai")
		tradee:sendMinitext("Tukar-menukar selesai")
	end,

	massExchangeWithdraw = function(player, npc, itemsToTrade, bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable)
		local found = 0
		local amount = 0
		local counter = 0
		local next = next

		i = 1
		while i <= #bankItemTable do
			if (bankItemTable[i] == 0) then
				table.remove(bankItemTable, i)
				table.remove(bankCountTable, i)
				table.remove(bankOwnerTable, i)
				table.remove(bankEngraveTable, i)
				table.remove(bankTimerTable, i)
				i = i - 1
			end
			i = i + 1
		end

		local bankItemTableNames = {}
		for i = 1, #bankItemTable do
			table.insert(bankItemTableNames, Item(bankItemTable[i]).name)
		end

		for i = 1, #bankItemTableNames do
			if bankOwnerTable[i] ~= 0 then
				bankItemTableNames[i] = bankItemTableNames[i] .. " - BONDED"
			end
		end

		local sortedbankItemTable = sort_relative(
			bankItemTableNames,
			bankItemTable
		)
		local sortedbankCountTable = sort_relative(
			bankItemTableNames,
			bankCountTable
		)
		local sortedbankOwnerTable = sort_relative(
			bankItemTableNames,
			bankOwnerTable
		)
		local sortedbankEngraveTable = sort_relative(
			bankItemTableNames,
			bankEngraveTable
		)
		local sortedbankTimerTable = sort_relative(
			bankItemTableNames,
			bankTimerTable
		)

		bankItemTable = sortedbankItemTable
		bankCountTable = sortedbankCountTable
		bankOwnerTable = sortedbankOwnerTable
		bankEngraveTable = sortedbankEngraveTable
		bankTimerTable = sortedbankTimerTable

		if (#bankItemTable == 0) then
			player:dialogSeq({"Simpananmu sedang kosong."})
			return false
		end

		local temp = player:buy(
			"What item do you want to add?",
			bankItemTable,
			bankCountTable,
			bankEngraveTable,
			bankOwnerTable,
			{},
			{},
			{}
		)

		for i = 1, 255 do
			if (Item(bankItemTable[i]).name == temp or bankEngraveTable[i] == temp) then
				found = i
				break
			end
		end

		if (found == 0) then
			return
		end

		if Item(bankItemTable[found]).exchangeable or Item(bankItemTable[found]).depositable then
			player:dialogSeq({"Barang itu tidak bisa diperdagangkan."})
			return
		end

		if (Item(bankItemTable[found]).stackAmount > 1 and bankCountTable[found] > 1) then
			amount = player:inputNumberCheck(player:input("Berapa banyak yang akan kau ambil untuk perdagangan ini?"))

			if (amount > bankCountTable[found]) then
				amount = bankCountTable[found]
			end
		else
			amount = 1
		end

		if (amount <= 0) then
			return
		end

		local item = {
			id = bankItemTable[found],
			amount = amount,
			name = Item(bankItemTable[found]).name,
			owner = bankOwnerTable[found],
			realName = bankEngraveTable[found],
			time = bankTimerTable[found]
		}

		bankCountTable[found] = bankCountTable[found] - amount
		table.insert(itemsToTrade, item)

		i = 1
		while i <= #bankCountTable do
			if (bankCountTable[i] <= 0) then
				table.remove(bankItemTable, i)
				table.remove(bankCountTable, i)
				table.remove(bankOwnerTable, i)
				table.remove(bankEngraveTable, i)
				table.remove(bankTimerTable, i)
				i = i - 1
			end
			i = i + 1
		end

		return itemsToTrade, bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable
	end,

	freeWorldShout = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if os.time() < player.registry["gave_fragile_orb_of_world_shout_time"] then
			player:dialogSeq(
				{
					t,
					"Kau baru saja menerima fragile orb of world shout."
				},
				0
			)
			return
		end

		player:dialogSeq(
			{
				t,
				"Ini fitur percobaan dan bersifat sementara. Fragile Orb of World Shout memungkinkanmu berseru ke seluruh dunia sekali tiap jam, dan bertahan 24 jam.",
				"Ini Fragile Orb of World Shout gratis untukmu."
			},
			1
		)
		player.registry["gave_fragile_orb_of_world_shout_time"] = os.time() + 86400
		player:addItem("fragile_orb_of_world_shout", 1, 0, 0, os.time() + 86400)
	end,

	time = function(player)
		local monthString = ""

		local year = curYear()
		local month = math.abs(os.date("%m"))
		local day = curDay()
		local dayString = ""
		local dayNames = {"Mon", "Tue", "Wed", "Thurs", "Fri", "Sat", "Sun"}
		local dayNames2 = dayNames[(curDay() % 7) + 1]
		local time = curTime()
		local timeString = ""

		if month == 1 then
			monthString = "st"
		elseif month == 2 then
			monthString = "nd"
		elseif month == 3 then
			monthString = "rd"
		elseif month >= 4 then
			monthString = "th"
		end

		if day == 1 then
			dayString = "st"
		elseif day == 2 then
			dayString = "nd"
		elseif day == 3 then
			dayString = "rd"
		elseif day >= 4 then
			dayString = "th"
		end

		if time <= 12 then
			timeString = "a.m."
		elseif time > 12 then
			timeString = "p.m."
			time = time - 12
		end

		player:sendMinitext("Yuri " .. year .. ", " .. month .. "" .. monthString .. " Moon, " .. day .. "" .. dayString)
		player:sendMinitext(dayNames2 .. " " .. time .. " " .. timeString)
	end,

	reincarnate = function(player, npc)
		if player.state ~= 1 then
			player:dialog("Kau sudah hidup.", {})
			return
		end
		resurrect.cast(player)
	end,

	observe = function(player, npc)
		if player.state ~= 1 then
			local choice2 = player:menuSeq(
				"Kau harus mati dulu untuk bisa mengamati. Tenang, tidak akan sakit sedikit pun. Kau siap?",
				{"Ya", "Tidak"},
				{}
			)
			if choice2 == 1 then
				onDismount(player)
				player.state = 1
				player:flushDuration(1)
				player:updateState()
			end
		end
	end,

	novices = function(player, npc, choice)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local novices_opts = {
			"Tolong!",
			"Tolong beri aku tur singkat.",
			"Keyboard commands",
			"Finding a place",
			"I'm lost!",
			"Karakterku",
			"I died!",
			"I'm naked",
			"Food",
			"Money",
			"Choosing a Path",
			"Bertualang bersama orang lain",
			"Joining a clan",
			"Joining a subpath",
			"Mendaftarkan karakterku",
			"Bagaimana cara menghubungi staf RetroTKeborn?",
			"Tunjukkan pokok Bantuan lainnya."
		}

		if choice == nil then
			choice = player:menuString(
				"Pilih satu pokok bantuan dari daftar di bawah",
				novices_opts
			)
		end

		if choice == "Tolong!" then
			player:dialogSeq(
				{
					t,
					"Kalau kau punya masalah yang menghalangimu bermain, mintalah bantuan di discord RTK untuk menyelesaikannya",
					"Kalau kau tidak yakin harus berbuat apa, coba baca papan Guide atau Welcome to RetroTK.",
					"Bertanya baik-baik kepada pemain lain juga cara yang bagus untuk mendapat bantuan."
				},
				1
			)
			general_npc_funcs.novices(player, npc)
		elseif choice == "Tolong beri aku tur singkat." then
			local countries = {"Kugnae", "Buya", "Nagnang"}

			local countryChoice = player:menuString(
				"Negeri mana yang ingin kau kunjungi?",
				countries
			)

			if countryChoice == "Kugnae" then
				local placeChoices = {
					"Rumah IronHeart (Tutorial)",
					"Inn",
					"Butcher",
					"Smith",
					"Seamster",
					"Seamstress",
					"Dawn Shaman",
					"Dusk Shaman",
					"Guild Prajurit",
					"Guild Rogue",
					"Guild Penyihir",
					"Guild Pujangga",
					"Woodlands Alchemist",
					"Vagabond Alchemist",
					"Arena",
					"Jail",
					"Chapel",
					"Palace",
					"I'm done"
				}

				local placeChoice = player:menuString(
					"Bagian mana dari Koguryo yang ingin kau kunjungi?",
					placeChoices
				)

				if placeChoice == "Rumah IronHeart (Tutorial)" then
					local tironheart = {
						graphic = convertGraphic(
							NPC("IronHeart").look,
							"monster"
						),
						color = NPC("IronHeart").lookColor
					}
					player:dialogSeq(
						{
							tironheart,
							"Banyak hal yang perlu kau pelajari di dunia RetroTKeborn. Sang tutor bisa mengajarkan sebagian di antaranya."
						},
						1
					)
					player:warp(36, 7, 6)
				elseif placeChoice == "Inn" then
					local tinn = {
						graphic = convertGraphic(NPC("Walsuk").look, "monster"),
						color = NPC("Walsuk").lookColor
					}
					player:dialogSeq(
						{
							tinn,
							"Temui kawan-kawanmu dan simpan barang berhargamu di sini. Kau juga bisa memakai gulungan kuning untuk kembali ke tempat ini."
						},
						1
					)
					player:warp(2, 18, 6)
				elseif placeChoice == "Butcher" then
					local tbutcher = {
						graphic = convertGraphic(NPC("Ogi").look, "monster"),
						color = NPC("Ogi").lookColor
					}
					player:dialogSeq(
						{
							tbutcher,
							"Kau bisa menjual acorn kepada tukang daging. Kalau kau prajurit, kau bisa membeli tanduk atau hati beruang untuk kesehatan."
						},
						1
					)
					player:warp(43, 6, 7)
				end

				--I'm done : It was my pleasure to be your guide. I hope you explore much more. Farewell.
			elseif countryChoice == "Buya" then
			elseif countryChoice == "Nagnang" then
			end
		elseif choice == "Keyboard commands" then
			local commands = {
				"Socializing",
				"Adventuring",
				"Melihat karakterku",
				"Tidak satu pun"
			}

			local commandChoice = player:menuString(
				"Apa yang perlu kubantu?",
				commands
			)

			if commandChoice == "Socializing" then
				player:dialogSeq(
					{
						t,
						" Bergaul\nBicara <petik tunggal>\nEkspresi <titik dua>\nBisik <shift><petik>\nPapan pengumuman<b>\nLihat pahlawan <ctrl>+<w>\nBuka grup <shift>+<g>\nTambah anggota grup <g>\nRiwayat <shift>+<panah atas>\nAbaikan seseorang <F9>"
					},
					1
				)
			elseif commandChoice == "Adventuring" then
				player:dialogSeq(
					{
						t,
						" Bertualang\nPakai barang <u>\nSerang <spasi>\nAmbil barang <koma>\nAmbil semua <shift>+<koma>\nBuka/Tutup <o>\nLihat <titik-koma>\nTunggangi kuda <r>\nLepas <shift>+<t>\nJatuhkan sesuatu <d>\nPakai bakat <shift>+<z>"
					},
					1
				)
			elseif commandChoice == "Melihat karakterku" then
				player:dialogSeq(
					{
						t,
						"Ambil surat <b>\nAmbil kiriman lewat kurir\nStatus <s>\nStatus lanjut <Page Down>\nSetelan <F10>\nKantong <i>\nPakai benda <u>\nPakai rahasia <shift>+<z>\nSerahkan benda <h>\nSerahkan banyak benda <shift>+<h>\nSerahkan keping <h>,<garis-miring-terbalik>"
					},
					1
				)
			elseif commandChoice == "Tidak satu pun" then
				return
			end

			general_npc_funcs.novices(player, npc, choice)
		elseif choice == "Finding a place" then
			player:dialogSeq(
				{
					t,
					"Halo, sepertinya kau sedang mencari sesuatu. Aku bisa membantumu menemukan banyak tempat di kota-kota utama."
				},
				1
			)

			local cities = {
				"Kugnae",
				"Buya",
				"Nagnang",
				"Apa arti angka-angka itu?",
				"I'm done"
			}

			local cityChoice = player:menuString(
				"Kota mana yang perlu kutunjukkan jalannya?",
				cities
			)

			if cityChoice == "Kugnae" then
				player:dialogSeq(
					{
						t,
						" Kugnae Shops\nInn 51,151\nButcher 41,131\nSmith 60,123\nSeamster 82,168\nSeamstress 83,160\nMessenger 95,48\nWood Alchemist 196,200\nVagbnd Alchemist 17,13\nDawn Shaman 198,94\nDusk Shaman 42,92"
					},
					1
				)
				player:dialogSeq(
					{
						t,
						"Balai Kugnae\nTutorial 88,148\nGuild Prajurit 48,37\nGuild Rogue 22,188\nGuild Penyihir 169,63\nGuild Pujangga 184,182\nArena 185,31\nPenjara/Pengadilan 156,157\nKapel 153,188\nIstana 111,122"
					},
					1
				)
			elseif cityChoice == "Buya" then
				player:dialogSeq(
					{
						t,
						" Buya Shops\nInn 39,105\nButcher 39,129\nSmith 18,103\nSeamster 19,127\nSeamstress 19,119\nMessenger 97,131\nStorm Shaman 125,58\nFelis Shaman 29,57"
					},
					1
				)
				player:dialogSeq(
					{
						t,
						" Balai Buya\nTutorial 55,122\nGuild Prajurit 26,25\nGuild Rogue 21,88\nGuild Penyihir 126,35\nGuild Pujangga 96,101\nArena 126,74\nPenjara/Pengadilan 10,50\nKapel 73,102\nIstana 73,54"
					},
					1
				)
			elseif cityChoice == "Nagnang" then
				player:dialogSeq(
					{
						t,
						" Nagnang Shops\nButcher 38,129\nSmith 21,88\nSeamster 18,127\nSeamstress 19,119\nMessenger 96,131\nMountain Shaman 55,122\nArena 73,103"
					},
					1
				)
				player:dialogSeq(
					{
						t,
						" Balai Nagnang\nGuild Prajurit 29,57\nGuild Rogue 19,141\nGuild Penyihir 126,35\nGuild Pujangga 96,101\nKedai Angin 82,131\nKedai Kayu 87,143\nKedai Air 97,122\nKedai Api 111,126\nKedai Logam 127,131"
					},
					1
				)
			elseif cityChoice == "Apa arti angka-angka itu?" then
				player:dialogSeq(
					{
						t,
						"Geser gulungan ini supaya kau bisa melihat angka di kanan bawah layarmu yang menunjukkan koordinatmu.",
						"Itulah posisimu di daerah ini. Saat kau bergerak ke Timur (kanan), angka pertama bertambah. Saat kau bergerak ke Selatan (bawah), angka kedua bertambah.",
						"Semua tempat ini mencantumkan pasangan angka yang dekat dengan pintu masuknya. Misalnya kalau kau mencari Tutorial di Kugnae, berjalanlah sampai angkanya menunjukkan sekitar 0095 0048."
					},
					1
				)
			end

			player:dialogSeq(
				{t, "Kalau kau butuh bantuan memakai angka-angka ini, bilang saja."},
				1
			)
			general_npc_funcs.novices(player, npc, choice)
		end
	end,

	changeFace = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local reject = "Ah, I see. Appear as thou wilt."

		local crime = player:menuString(
			"Kau tidak sedang dicari karena kejahatan, kan?",
			{"Ya", "Tidak"}
		)

		if crime == "Ya" then
			player:dialogSeq({t, reject}, 0)
			return
		elseif crime == "Tidak" then
			-- maybe do some crime check here?

			if player.money < 3000 then
				player:dialogSeq(
					{
						t,
						"Harganya 3.000 keping. Kembalilah kalau kau sudah punya."
					},
					0
				)
				return
			end

			local cost = player:menuString(
				"Harganya 3.000 keping. Kau bersedia membayar?",
				{"Ya", "Tidak"}
			)

			if cost == "Ya" then
				player:dialogSeq(
					{
						t,
						"Pilih wajah yang kau suka. Hati-hati, perubahannya permanen. Pakai 'Sebelumnya' dan 'Berikutnya' untuk membolak-balik pilihan wajah."
					},
					1
				)

				player.dialogType = 2
				player.lastClick = player.ID

				clone.equip(player, player)

				local faces = {
					200,
					201,
					202,
					203,
					204,
					205,
					206,
					207,
					208,
					209,
					210,
					211,
					212,
					213,
					214,
					215,
					216
				}

				local index = 1

				local menu = {
					"Aku mau yang ini",
					"Lupakan saja",
					"Wajah berikutnya",
					"Wajah sebelumnya"
				}
				local menuChoice = ""

				local str = "buff"

				while str == "buff" do
					--local face = faces[index]
					player.gfxFace = faces[index]

					menuChoice = player:menuString(
						"Kau suka wajah ini?",
						menu
					)

					if menuChoice == "Aku mau yang ini" then
						if player.money < 3000 then
							player:dialogSeq(
								{
									t,
									"Harganya 3.000 keping. Kembalilah kalau kau sudah punya."
								},
								0
							)
							return
						end

						player.face = player.gfxFace
						player:removeGold(3000)
						player:dialogSeq(
							{
								t,
								"Membentuk daging ini tidak mudah. Mari lihat hasilnya."
							},
							1
						)
						player:sendAnimation(11, 5)
						player:updateState()
						return
					elseif menuChoice == "Wajah berikutnya" then
						index = index + 1
						if index > #faces then
							index = #faces
						end
						player.gfxFace = faces[index]
					elseif menuChoice == "Wajah sebelumnya" then
						index = index - 1
						if index < 1 then
							index = 1
						end
						player.gfxFace = faces[index]
					elseif menuChoice == "Nevermind" then
						player.state = 0
						return
					end

					--[[elseif menuChoice == "Next face" then
					player.gfxFace = player.gfxFace + 1
					if player.gfxFace > 238 then player.gfxFace = 238 end

				elseif menuChoice == "Previous face" then
					player.gfxFace = player.gfxFace - 1
					if player.gfxFace < 200 then player.gfxFace = 200 end

				end]]
					--
				end
			elseif cost == "Tidak" then
				player:dialogSeq({t, reject}, 0)
				return
			end
		end
	end,

	changeGender = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		-- you must be unwed to change your gender.
		-- You must remove everything you are wearing before you can change your gender.

		if player:isEquipped() then
			-- player is equipped
			local choice = player:menuSeq(
				"Kau harus melepas semua yang kau kenakan sebelum bisa mengubah jenis kelaminmu. Lepas barangmu sekarang?\n(Kalau kantongmu penuh, barangnya AKAN dijatuhkan.)",
				{"Ya, lepaskan semuanya", "Tidak, aku bisa melepasnya sendiri"},
				{}
			)

			if choice == 1 then
				-- strip
				for i = 0, 14 do
					player:stripEquip(i, 0, 1)
				end
			end
		end

		if player.money < 12000 then
			player:dialogSeq(
				{
					t,
					"Kau butuh 12.000 emas untuk mengubah jenis kelamin. Kembalilah kalau uangnya sudah ada."
				},
				0
			)
			return
		end

		local confirm = player:menuString(
			"Kau sadar kau tidak akan bisa mengenakan pakaian yang biasa kau pakai, bukan?",
			{"Ya", "Tidak"}
		)

		if confirm == "Ya" then
			local confirmSexChange = ""

			if player.sex == 0 then
				-- male
				confirmSexChange = player:menuString(
					"Apakah kau ingin menjadi perempuan?",
					{"Ya", "Tidak"}
				)
			elseif player.sex == 1 then
				-- female
				confirmSexChange = player:menuString(
					"Apakah kau ingin menjadi lelaki?",
					{"Ya", "Tidak"}
				)
			end

			if confirmSexChange == "Ya" then
				player:removeGold(12000)

				local text = ""
				if player.sex == 0 then
					player.sex = 1
					text = "seamstress's"
				elseif player.sex == 1 then
					player.sex = 0
					text = "seamster's"
				end

				player:sendAnimation(8, 5)
				player:updateState()

				player:dialogSeq(
					{
						t,
						"Nah, wah, itu kerja keras.",
						"Sekarang kau bisa berbelanja di toko " .. text .. "."
					},
					0
				)
				return
			end
		elseif confirm == "Tidak" then
			player:dialogSeq({t, "Baiklah. Mungkin kau memang lebih baik seperti sekarang."}, 0)
			return
		end
	end,

	changeEyes = function(player, npc)
		local eyeChoice = player:menuString(
			"Tidak semua mata tampak sama ketika warnanya diubah. Kau yakin ingin melakukannya?",
			{"Ya", "Tidak"}
		)

		if eyeChoice == "Ya" then
			if player.money < 5000 then
				player:dialogSeq(
					{
						t,
						"Harganya 5.000 keping. Kembalilah kalau kau sudah punya."
					},
					0
				)
				return
			end

			local cost = player:menuString(
				"Harganya 5.000 keping. Kau bersedia membayar?",
				{"Ya", "Tidak"}
			)

			if cost == "Ya" then
				player:dialogSeq(
					{
						t,
						"Pilih mata yang kau suka. Hati-hati, perubahannya permanen. Pakai 'Sebelumnya' dan 'Berikutnya' untuk membolak-balik pilihannya."
					},
					1
				)

				player.dialogType = 2
				player.lastClick = player.ID

				clone.equip(player, player)

				local menu = {
					"Aku mau yang ini",
					"Lupakan saja",
					"Warna mata berikutnya",
					"Warna mata sebelumnya"
				}
				local menuChoice = ""

				local str = "buff"

				while str == "buff" do
					menuChoice = player:menuString(
						"Kau suka warna mata ini?",
						menu
					)

					if menuChoice == "Aku mau yang ini" then
						player.faceColor = player.gfxFaceC
						player:removeGold(3000)
						player:dialogSeq(
							{
								t,
								"Memasang mata baru ini tidak mudah. Mari lihat hasilnya."
							},
							1
						)
						player:sendAnimation(11, 5)
						player:updateState()
						return
					elseif menuChoice == "Warna mata berikutnya" then
						player.gfxFaceC = player.gfxFaceC + 1
						if player.gfxFaceC > 64 then
							player.gfxFaceC = 64
						end
					elseif menuChoice == "Warna mata sebelumnya" then
						player.gfxFaceC = player.gfxFaceC - 1
						if player.gfxFaceC < 0 then
							player.gfxFaceC = 0
						end
					end
				end
			elseif cost == "Tidak" then
				player:dialogSeq({t, reject}, 0)
				return
			end
		elseif eyeChoice == "Tidak" then
			player:dialogSeq(
				{t, "Menurutku matamu sudah bagus apa adanya!"},
				0
			)
			return
		end
	end,

	haircut = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		--if player.hair == 62 then -- bald
		--	player:dialogSeq({t,"Oooooh, look at all that scarring on the top of your head there. You didn't care for your hair well, so it fell out. I'm afraid there is nothing I can do for you until your hair grows back."},0)
		--return
		--end

		--[[if os.time() < player.registry["haircutTimer"] then
		player:dialogSeq({t,"Oh no. Your hair is so fine and wispy. You must have had work done recently."},1)

		local choice = player:menuSeq("Your hair might fall out! Wait until it grows some strength again, then come and see me.",{"Ok, I'll come back later.","I'm not worried, change my hair!!"},{})

		if choice == 2 then
			player:dialogSeq({t,"Alright. You certainly know what you want! Who am I to refuse such a determined customer.."},1)
		end
	end]]
		--

		if player.money < 2000 then
			player:dialogSeq(
				{
					t,
					"Potong rambut harganya 2.000 keping. Temui aku kalau kau sanggup membayarnya."
				},
				0
			)
			return
		end

		if npc.mapTitle == "Kugnae Salon" then
			player:dialogSeq({t, "Potong rambut harganya 2.000 keping."}, 1)
		elseif npc.mapTitle == "Buya Salon" then
			player:dialogSeq(
				{
					t,
					"Dengan 2.000 keping kau bisa dapat gaya yang benar-benar keren, kawan.."
				},
				1
			)
		elseif npc.mapTitle == "Nagnang Salon" then
			player:dialogSeq({t, "Potong rambut harganya 2.000 keping."}, 1)
		end

		local styles = {}
		local hairs = {}

		local index = 1

		player.dialogType = 2
		clone.equip(player, player)

		if npc.mapTitle == "Kugnae Salon" then
			if player.sex == 0 then
				styles = {
					"Long tied back",
					"Medium razorback",
					"Short razor cut",
					"Long tied slick hair",
					"Tarzan",
					"Sumo's do",
					"The Surf",
					"Floor sweeper",
					"Chopsticks"
				}
				hairs = {7, 22, 23, 52, 55, 58, 85, 53, 95}
			elseif player.sex == 1 then
				styles = {
					"Long, loose hair",
					"Pulled up bunches",
					"The Updo",
					"Long pig tails",
					"Long stylish pony tail",
					"Medium blunt cut",
					"Whale back",
					"Long horse tail",
					"Medium V cut",
					"The Flippy",
					"Pig tails",
					"Short cute bob",
					"Sweet rolls",
					"Two little braids",
					"Innocent with headband",
					"Long low ribbon",
					"The Floor sweeper",
					"Jane",
					"Kimono hair",
					"The Twisler",
					"Long elegant ponytail",
					"Long pinned weavey locks",
					"Teddy bear ears",
					"Left shoulder braid",
					"Medium under curls",
					"Walnut whip",
					"Tinker toys",
					"Medium ponytail",
					"Chopsticks",
					"Twisted medley",
					"Long lovely waves",
					"Long pocahontas braid"
				}
				hairs = {
					4,
					6,
					12,
					13,
					16,
					22,
					23,
					25,
					28,
					31,
					35,
					38,
					41,
					45,
					50,
					52,
					53,
					55,
					58,
					63,
					64,
					65,
					66,
					67,
					72,
					83,
					84,
					88,
					95,
					96,
					98,
					99
				}
			end
		elseif npc.mapTitle == "Buya Salon" then
			if player.sex == 0 then
				styles = {
					"Mullet with bandana",
					"Chieftain",
					"Leather weave",
					"Split curtain",
					"The Pineapple",
					"Widge cut with bandana",
					"Masked Bandit",
					"Mushroom cut",
					"Wing tips",
					"Buddha long top knot",
					"The Jester",
					"The ramp",
					"Afro",
					"Dread locks",
					"Retro cut",
					"Ice cream whip",
					"Fluff cut",
					"The Wooly hair",
					"Dual horns",
					"Swept out",
					"The Clown",
					"The Elvis",
					"Short snake tail"
				}
				hairs = {
					8,
					9,
					11,
					14,
					15,
					20,
					24,
					33,
					37,
					57,
					61,
					73,
					74,
					75,
					76,
					77,
					78,
					81,
					86,
					87,
					93,
					94,
					100
				}
			elseif player.sex == 1 then
				styles = {
					"Amazon",
					"Eggbeater",
					"Dread locks",
					"Veiled mask",
					"Fluff cut",
					"Girly tails",
					"Short and curly",
					"Mullet with bandana",
					"Cupie doll",
					"Long layered cut",
					"Short and sassy",
					"Rounded weave",
					"Soft waves",
					"Top bun",
					"Honey buns",
					"Bun with puppy tails",
					"Short cupie with tails",
					"Feathered with headband",
					"Short waves"
				}
				hairs = {
					9,
					74,
					75,
					24,
					78,
					92,
					5,
					8,
					18,
					54,
					20,
					79,
					82,
					34,
					91,
					27,
					30,
					39,
					44
				}
			end
		elseif npc.mapTitle == "Nagnang Salon" then
			if player.sex == 0 then
				styles = {
					"Love fringe",
					"Short fringe",
					"Mullet",
					"Ceasar cut",
					"Long ceasar",
					"West wing cut",
					"East wing cut",
					"Bald",
					"The Windmill",
					"Curlt top (short)",
					"Curl top with bandana",
					"Balding crew cut",
					"Notch cut",
					"Brush cut",
					"Buzz cut",
					"Curly crew cut",
					"Flat top",
					"Hedgehog",
					"Razor cut with bandana",
					"East swept with bangs",
					"Fire swept",
					"Prickly",
					"Short ruffled curltains",
					"Quiff",
					"The Pirate",
					"Bowl cut"
				}
				hairs = {
					174,
					2,
					1,
					3,
					10,
					17,
					49,
					19,
					21,
					69,
					26,
					36,
					40,
					43,
					46,
					47,
					48,
					51,
					56,
					59,
					60,
					68,
					71,
					80,
					90,
					70
				}
			elseif player.sex == 1 then
				styles = {
					"Pixie cut",
					"Curl top with bandana",
					"Short with body",
					"Pixie cute",
					"Page boy"
				}
				hairs = {10, 26, 32, 70, 42}
			end
		end

		local opts = {
			"Ya, silakan!",
			"Gaya rambut berikutnya",
			"Gaya rambut sebelumnya",
			"Ya, tetapi warnanya kurang pas."
		}

		local str = "buff"

		while str == "buff" do
			player.gfxHair = hairs[index]

			local optsChoice = player:menuString(
				"<b>Style: " .. styles[index] .. "\n\nApakah gaya ini sudah cocok?",
				opts
			)

			if optsChoice == "Ya, silakan!" then
				local confirm = player:menuSeq(
					"Biayanya 2.000 keping. Kau mau membayar?",
					{"Ini 2.000.", "Aku menolak membayar."},
					{}
				)

				if confirm == 1 then
					if player.money < 2000 then
						player:dialogSeq(
							{
								t,
								"Potong rambut harganya 2.000 keping. Temui aku kalau kau sanggup membayarnya."
							},
							0
						)
						return
					end

					player:dialogSeq(
						{
							t,
							"Bagus! Biar kuambil guntingku..",
							"Sedikit di sini... [*kres*]  Sedikit di sana... [*kres*]"
						},
						1
					)
					player:removeGold(2000)

					--if os.time() < player.registry["haircutTimer"] then -- make bald
					--	player.hair = 62 -- bald
					--	player:updateState()
					--	player:dialogSeq({t,"Oh no! Your hair fell out! It was too fragile. I told you that you should have waited.. now see what happened? You'll be stuck like this until it grows back!"},0)
					--else
					player.hair = hairs[index]
					player:updateState()

					--player.registry["haircutTimer"] = os.time() + 10800 -- 3 hours
					player:dialogSeq(
						{t, "Selesai! Nikmati gaya rambut barumu!"},
						0
					)

					--end
				end
			elseif optsChoice == "Gaya rambut berikutnya" then
				index = index + 1
				if index > #styles then
					index = #styles
				end
			elseif optsChoice == "Gaya rambut sebelumnya" then
				index = index - 1
				if index < 1 then
					index = 1
				end
			elseif optsChoice == "Ya, tetapi warnanya kurang pas." then
				player:dialogSeq(
					{
						t,
						"Aku mengerti. Akan kuusahakan menyamai warna rambutmu sekarang setelah selesai memotongnya. Bicaralah lagi kepadaku setelah selesai kalau kau mau warna rambut baru."
					},
					1
				)
			end
		end
	end,

	hairdye = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if npc.mapTitle ~= "Nagnang Salon" then
			player:dialogSeq(
				{
					t,
					"Warna baru bisa menghidupkan semangatmu dan menyegarkan penampilanmu! Pas sekali untuk mencerahkan harimu!"
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Aku bisa menawarkan berbagai pewarna rambut bermutu hanya seharga 2.000 keping."
				},
				1
			)
		else
			player:dialogSeq(
				{
					t,
					"Warna baru ya? Boleh, aku bisa bantu. Aku cuma menyediakan warna yang bagus. Kalau kau tidak suka pilihanku, pergi sana."
				},
				1
			)
			player:dialogSeq(
				{t, "Pewarna rambut harganya 2.000 keping. Bayar atau pergi."},
				1
			)
		end

		if player.money < 2000 then
			player:dialogSeq(
				{t, "Jadi kembalilah kalau uangmu sudah cukup, sayang!"},
				0
			)
			return
		end

		local index = 1
		local hairColors = {}
		local hairC = {}

		if npc.mapTitle == "Kugnae Salon" then
			hairColors = {
				"Black",
				"Silver",
				"Brown",
				"Sky blue",
				"Dark blue",
				"Royal blue",
				"Orange",
				"Red",
				"Green",
				"Scarlet"
			}
			hairC = {0, 1, 2, 8, 7, 24, 10, 11, 22, 21}
		elseif npc.mapTitle == "Buya Salon" then
			hairColors = {
				"Black",
				"Silver",
				"Brown",
				"Light brown",
				"Tan",
				"Blonde",
				"Orange",
				"Red",
				"Green",
				"Scarlet"
			}
			hairC = {0, 1, 2, 27, 25, 20, 10, 11, 22, 21}
		elseif npc.mapTitle == "Nagnang Salon" then
			hairColors = {
				"Black",
				"Silver",
				"Brown",
				"Orchid",
				"Purple",
				"Indigo",
				"Orange",
				"Red",
				"Green",
				"Scarlet"
			}
			hairC = {0, 1, 2, 3, 9, 29, 10, 11, 22, 21}
		end

		player.dialogType = 2
		clone.equip(player, player)

		local opts = {
			"Ya, silakan!",
			"Warna rambut berikutnya",
			"Warna rambut sebelumnya",
			"Nevermind"
		}

		local str = "buff"

		while str == "buff" do
			player.gfxHairC = hairC[index]

			local optsChoice = player:menuString(
				"<b>Color: " .. hairColors[index] .. "\n\nApakah gaya ini sudah cocok?",
				opts
			)

			if optsChoice == "Ya, silakan!" then
				local confirm = player:menuSeq(
					"Jadinya 2.000 keping. Kau mau membayar?",
					{"Ya, ini uangnya.", "Aku tidak mau membayar sebanyak itu."},
					{}
				)

				if confirm == 1 then
					if player.money < 2000 then
						player:dialogSeq(
							{t, "Kembalilah kalau uangnya sudah ada."},
							0
						)
						return
					end

					player:dialogSeq(
						{t, "Ini pekerjaan yang sangat halus. Jangan bergerak.."},
						1
					)
					player:removeGold(2000)
					player.hairColor = hairC[index]
					player:updateState()

					player:dialogSeq(
						{t, "Nah! Sekarang kau kelihatan JAUH lebih baik, kan!"},
						0
					)

					-- Oh one more thing. Your hair is very fragile now. I would not suggest getting a dye or style change for awhile. It could cause your hair to fall out permanently!
				end
			elseif optsChoice == "Warna rambut berikutnya" then
				index = index + 1
				if index > #hairColors then
					index = #hairColors
				end
			elseif optsChoice == "Warna rambut sebelumnya" then
				index = index - 1
				if index < 1 then
					index = 1
				end
			elseif optsChoice == "nevermind" then
				return
			end
		end
	end,

	shave = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player:getEquippedItem(EQ_FACEACCTWO) == nil then
			player:dialogSeq({t, "Hei, kau tidak punya janggut!"}, 0)
			return
		end

		local choices = {"Aku akan membayarnya.", "Aku menolak membayar!"}

		if player.money < 2500 then
			player:dialogSeq({t, "Kembalilah kalau kau sudah punya 2.500 keping."}, 0)
			return
		end

		local choice = player:menuString(
			"Mencukur janggutmu harganya 2.500 keping.",
			choices
		)

		if choice == "Aku akan membayarnya." then
			player:dialogSeq({t, "Diam sebentar.."}, 1)
			player:stripEquip(EQ_FACEACCTWO, 1, 0)
			player:removeGold(2500)

			player:dialogSeq(
				{t, "Nah, selesai. Nikmati wajahmu yang baru dicukur!"},
				0
			)
		end
	end,

	beard = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player:getEquippedItem(EQ_FACEACCTWO) ~= nil then
			-- slot 14
			player:dialogSeq(
				{
					t,
					"Kau harus mencukur janggutmu dulu sebelum bisa mengganti gaya!"
				},
				0
			)
			return
		end

		player:dialogSeq(
			{
				t,
				"Janggut baru? Oh, kau akan tampak menawan dengan salah satu gayaku!",
				"Perlu kau tahu, keahlianku tidak gratis, dan ramuan penumbuh rambut itu mahal!\n\nAku memungut 2.500 untuk jasaku, dan 11.000 untuk pemakaian ramuannya."
			},
			1
		)

		local choice = player:menuSeq(
			"Total untuk janggut barumu 13.500. Kau mau membayarnya?",
			{
				"Tentu, aku akan membayar 13.500.",
				"Tidak, itu keterlaluan! Aku menolak."
			},
			{}
		)

		local index = 1
		local beards = {}
		local beardItems = {
			"black_short_beard",
			"brown_short_beard",
			"black_full_beard",
			"brown_full_beard",
			"black_moustache",
			"brown_moustache",
			"black_whiskers",
			"brown_whiskers"
		}

		for i = 1, #beardItems do
			table.insert(beards, Item(beardItems[i]).name)
		end

		if choice == 1 then
			player.dialogType = 2
			clone.equip(player, player)

			local opts = {
				"Ya, silakan!",
				"Janggut berikutnya",
				"Janggut sebelumnya",
				"Nevermind"
			}

			local str = "buff"

			while str == "buff" do
				player.gfxFaceAT = Item(beardItems[index]).look
				player.gfxFaceATC = Item(beardItems[index]).lookC

				local optsChoice = player:menuString(
					"<b>Beard style: " .. beards[index] .. "\n\nApakah gaya ini sudah cocok?",
					opts
				)

				if optsChoice == "Ya, silakan!" then
					player:dialogSeq(
						{
							t,
							"Bagus! Biar kuambil satu botol lagi ramuan penumbuh rambutku. Tunggu sebentar, sayang."
						},
						1
					)
					local confirm = player:menuSeq(
						"Perlu kau tahu, janggut ini permanen. Kau tidak bisa menghilangkannya sampai kembali ke sini untuk dicukur. Masih mau janggut baru?",
						{
							"Ya, aku paham janggut itu permanen.",
							"Hmmm, tidak bisa dihilangkan? Kalau dipikir-pikir lagi.."
						},
						{}
					)

					if confirm == 1 then
						if player.money < 13500 then
							player:dialogSeq(
								{t, "Kembalilah kalau uangnya sudah ada."},
								0
							)
							return
						end

						player:forceEquip(
							Item(beardItems[index]).id,
							EQ_FACEACCTWO
						)

						player:dialogSeq(
							{
								t,
								"Baiklah, kita siap mulai! Jangan bergerak selagi kuoleskan ramuannya.."
							},
							1
						)
						player:removeGold(13500)
						clone.wipe(player)
						player:updateState()

						player:dialogSeq(
							{t, "Nah! Sekarang kau kelihatan JAUH lebih baik, kan!"},
							0
						)
					end
				elseif optsChoice == "Janggut berikutnya" then
					index = index + 1
					if index > #beards then
						index = #beards
					end
				elseif optsChoice == "Janggut sebelumnya" then
					index = index - 1
					if index < 1 then
						index = 1
					end
				elseif optsChoice == "nevermind" then
					return
				end
			end
		elseif choice == 2 then
			player:dialogSeq({t, "Kalau begitu jasaku bukan untukmu."}, 0)
			return
		end
	end,

	scalpMassage = function(player, npc)
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
				t,
				"Ehmm, mungkin nanti kalau kau, yah, benar-benar pernah mengalami sesuatu yang melelahkan dalam hidupmu?"
			},
			0
		)

		--You don't look so good. Grab a soup bowl, rest up, and then we can reschedule!

		player:dialogSeq(
			{
				t,
				"Aku bisa melenyapkan penatmu hanya dengan sekali bayar 50.000 keping!",
				"((Fitur ini mengurangi manamu satu angka. Lanjutkan hanya kalau kau rela kehilangan satu angka mana))."
			},
			1
		)

		local choice = player:menuString("Kau lanjutkan?", {"Ya", "Tidak"})

		if choice == "Ya" then
			player:dialogSeq(
				{
					t,
					"Bagus sekali! *ia menepukkan kedua telapak tangannya lalu mulai menabuh kepalamu*",
					"*Kepalamu terasa pening, terguncang, dan lebih parah dari sebelumnya*"
				},
				0
			)
		end
	end,
}
