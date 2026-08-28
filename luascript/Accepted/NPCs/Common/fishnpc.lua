FishNpc = {
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

		math.randomseed(os.time())

		if (speech == "ikan" or speech == "aku ingin memancing" or speech == "aku ingin memancing") then
			Tools.checkKarma(player)

			if player.state == 1 then
				player:dialogSeq({t, "Temui aku lagi kalau kau sudah hidup."}, 0)
				return
			end

			if player.level <= 15 or player.quest["learned_to_fish"] == 0 then
				player:dialogSeq(
					{
						t,
						"Kau masih terlalu muda! Kalau sekarang kau menekuni memancing, kau tidak akan jadi apa-apa. Ah, ya sudah. Ini benang dan cacing untuk kau coba, semoga berhasil!"
					},
					1
				)

				local random = math.random(1, 50)

				if (random <= 5) then
					player.quest["learned_to_fish"] = 1
					player:addItem("minnow", 1)
					player:dialogSeq({t, "Kau mendapat seekor ikan!"}, 0)
				else
					player:dialogSeq(
						{
							t,
							"Kau memancing cukup lama, tetapi hasilnya sedikit."
						},
						0
					)
				end
			else
				-- level 15 +
				player:dialogSeq(
					{
						t,
						"Kau suka memancing, ya? Begini saja... aku tidak punya perlengkapan cadangan untuk dijual, tetapi kau boleh meminjam."
					},
					1
				)

				if player.baseHealth < 10 then
					player:dialogSeq(
						{t, "Kau butuh kesehatan dasar sedikitnya 10 untuk memancing."},
						0
					)
					return
				end

				if math.random(1, 10) == 1 then
					player.baseHealth = player.baseHealth - 1
					player:calcStat()
					player.registry["baseHealth"] = player.baseHealth
					player:dialogSeq(
						{
							t,
							"Sepertinya berat badanmu bertambah. Kesehatanmu mulai terganggu oleh kemalasanmu."
						},
						1
					)
				elseif math.random(1, 40) == 2 then
					player:removeKarma(0.001)
					player:dialogSeq(
						{
							t,
							"Kau mulai merasa sangat bersalah karena terlalu banyak memancing dan mengabaikan tanggung jawabmu yang lain."
						},
						1
					)
				end

				local poles = {
					"Sekadar seutas benang (1 emas)",
					"Tongkat dan benang (10 emas)",
					"Tongkat dan benang bagus (100 emas)",
					"Tongkat bagus dan benang bagus (1000 emas)"
				}
				local polesCost = {1, 10, 100, 1000}

				local bates = {
					"Tidak ada (0 emas)",
					"Cacing (5 emas)",
					"Serangga (50 emas)",
					"Umpan istimewa (500 emas)"
				}
				local batesCost = {0, 5, 50, 500}

				local poleChoice = player:menuSeq(
					"Joran jenis apa yang kau mau?",
					poles,
					{}
				)

				local bateChoice = player:menuSeq(
					"Sekarang, umpan jenis apa yang kau mau?",
					bates,
					{}
				)

				if poleChoice == nil or bateChoice == nil then
					return
				end

				if poleChoice > #polesCost then
					poleChoice = polesCost[#polesCost]
				end
				if bateChoice > #batesCost then
					bateChoice = batesCost[#batesCost]
				end

				local totalCost = polesCost[poleChoice] + batesCost[bateChoice]
				if player.money < totalCost then
					player:dialogSeq(
						{t, "Kembalilah kalau emasmu sudah cukup."},
						0
					)
					return
				end

				player:removeGold(totalCost)
				player:dialogSeq({t, "Suit yourself."}, 1)

				local fish = {
					"small_fish",
					"tiny_fish",
					"scrawny_fish",
					"tasty_fish",
					"large_fish",
					"delicious_fish"
				}
				local chosenFish = ""

				local maxRoll = 100

				if not crafting.checkSkillLevel(player, "fishing", "adept") then
					chosenFish = fish[math.random(1, 2)]
				elseif not crafting.checkSkillLevel(
					player,
					"fishing",
					"accomplished"
				) then
					chosenFish = fish[math.random(1, 2)]
					maxRoll = maxRoll - 2
				elseif not crafting.checkSkillLevel(player, "fishing", "adept") then
					chosenFish = fish[math.random(1, 3)]
					maxRoll = maxRoll - 4
				elseif not crafting.checkSkillLevel(
					player,
					"fishing",
					"talented"
				) then
					chosenFish = fish[math.random(2, 4)]
					maxRoll = maxRoll - 6
				elseif not crafting.checkSkillLevel(
					player,
					"fishing",
					"skilled"
				) then
					chosenFish = fish[math.random(3, 4)]
					maxRoll = maxRoll - 7
				elseif not crafting.checkSkillLevel(player, "fishing", "expert") then
					chosenFish = fish[math.random(3, 4)]
					maxRoll = maxRoll - 8
				elseif not crafting.checkSkillLevel(player, "fishing", "master") then
					chosenFish = fish[math.random(4, 6)]
					maxRoll = maxRoll - 9
				elseif not crafting.checkSkillLevel(
					player,
					"fishing",
					"grandmaster"
				) then
					chosenFish = fish[math.random(5, 6)]
					maxRoll = maxRoll - 10
				elseif not crafting.checkSkillLevel(
					player,
					"fishing",
					"champion"
				) then
					chosenFish = fish[math.random(6)]
					maxRoll = maxRoll - 12
				else
					chosenFish = fish[math.random(1, 2)]
				end

				if totalCost == 1 then
					maxRoll = maxRoll - 12
				elseif totalCost > 1 and totalCost < 10 then
					maxRoll = maxRoll - 20
				elseif totalCost >= 10 and totalCost < 100 then
					maxRoll = maxRoll - 28
				elseif totalCost >= 100 and totalCost < 200 then
					maxRoll = maxRoll - 34
				elseif totalCost >= 200 and totalCost < 500 then
					maxRoll = maxRoll - 42
				elseif totalCost >= 500 then
					maxRoll = maxRoll - 50
				else
					maxRoll = maxRoll - 12
				end

				math.randomseed(os.time())
				local roll = math.random(1, 100)

				crafting.skillChanceIncrease(player, npc, "fishing")

				if roll >= maxRoll then
					-- success

					math.randomseed(os.time())

					local magicalFishChance = 200
					if core.gameRegistry["fishing_magical_fish"] == 0 then
						core.gameRegistry["fishing_magical_fish"] = magicalFishChance
					end

					if math.random(1, core.gameRegistry["fishing_magical_fish"]) == 1 then
						-- magical fish

						core.gameRegistry["fishing_magical_fish"] = magicalFishChance
						local magicalFishChoice = player:menuSeq(
							"Kau menangkap ikan bersihir! Apa yang kau lakukan?",
							{"Simpan ikannya.", "Lepaskan ikannya kembali."},
							{}
						)

						if magicalFishChoice == 1 then
							player:addItem("magical_fish", 1)
						elseif magicalFishChoice == 2 then
							if math.random(1, 5) == 2 then
								player:addItem("sea_melodies", 1)
							end
						end

						return
					else
						core.gameRegistry["fishing_magical_fish"] = core.gameRegistry[
							"fishing_magical_fish"
						] - 1
					end

					math.randomseed(os.time())

					local lineStuckChance = math.random(5000, 10000)

					if core.gameRegistry["fishing_line_stuck"] == 0 then
						core.gameRegistry["fishing_line_stuck"] = lineStuckChance
					end

					if math.random(1, core.gameRegistry["fishing_line_stuck"]) == 1 then
						-- line stuck
						core.gameRegistry["fishing_line_stuck"] = lineStuckChance

						local stuckChoice = player:menuSeq(
							"Sepertinya senarmu tersangkut sesuatu. Kau:",
							{"Mencoba menariknya paksa.", "Membuang senarmu."},
							{}
						)

						if stuckChoice == 1 then
							-- attempt to free the line

							math.randomseed(os.time())

							local roll = math.random(1, 100)

							if roll <= 15 then
								-- 15% chance to actually get something

								math.randomseed(os.time())

								-- generate new seed
								local subroll = math.random(1, 100)

								local item

								--[[local items = {"star_scale_mail","moon_scale_mail","star_war_platemail","moon_war_platemail",
										"star_mail_dress","moon_mail_dress","star_war_dress","moon_war_dress","star_armor",
										"moon_armor","star_armor_dress","moon_armor_dress","star_clothes","moon_clothes",
										"star_skirt","moon_skirt","star_mantle","moon_mantle","star_drapery","moon_drapery",
										"star_helm","moon_helm","star_helmet","moon_helmet","star_garb","moon_garb","star_dress",
										"moon_dress","star_robes","moon_robes","star_blouse","moon_blouse","star_waistcoat","moon_waistcoat"}]]
								--

								-- Moon platemail, armor, mantle, clothes and female equivalent should never drop from fishing
								local items = {
									"star_scale_mail",
									"star_war_platemail",
									"star_mail_dress",
									"star_war_dress",
									"star_armor",
									"star_armor_dress",
									"star_clothes",
									"star_skirt",
									"star_mantle",
									"star_drapery",
									"star_helm",
									"moon_helm",
									"star_helmet",
									"moon_helmet"
								}

								--if subroll == 1 and os.time() > core.gameRegistry["fishing_sevenleaf_timer"] then
								if subroll == 1 then
									item = Item("sevenleaf_sword")

									--core.gameRegistry["fishing_sevenleaf_timer"] = os.time() + 31536000
								else
									item = Item(items[math.random(1, #items)])
								end

								local titem = {
									graphic = item.icon,
									color = item.iconColor
								}
								player:addItem(item.yname, 1)
								broadcast(
									-1,
									"[SYSTEM]: " .. player.name .. " has just fished up a " .. item.name .. "!"
								)
								player:dialogSeq(
									{
										titem,
										"Beruntung sekali! Senarmu tersangkut pada " .. item.name .. "!!! Setelah perjuangan yang melelahkan, " .. item.name .. " jadi milikmu!"
									},
									0
								)
							else
								-- death
								broadcast(
									-1,
									"[SYSTEM]: " .. player.name .. " has met their demise from a stuck fishing line."
								)
								player:sendMinitext("Senarmu terlepas dan membunuhmu dalam prosesnya.")
								player.health = 0
								onDeathPlayer(player)
							end
						end
						return
					else
						core.gameRegistry["fishing_line_stuck"] = core.gameRegistry[
							"fishing_line_stuck"
						] - 1
					end

					player:addItem(chosenFish, 1)

					player:dialogSeq({t, "Kau mendapat seekor ikan!"}, 0)
				else
					player:dialogSeq(
						{t, "Kau memancing sebentar, tetapi hasilnya sedikit."},
						0
					)
					return
				end
			end
		end
	end)
}
