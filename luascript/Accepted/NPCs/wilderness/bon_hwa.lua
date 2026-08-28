BonHwaNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.m ~= npc.m then
			return
		end

		if ((player.baseHealth < 80000 and player.baseMagic < 40000) or player.level < 99) then
			player:dialogSeq(
				{
					t,
					"Nekat sekali kau datang ke sini, selemah dirimu. Tidak ada yang bisa kulakukan untukmu."
				},
				0
			)
			return
		end

		local options = {"Bon-Hwa Immortality", "Shadow Stats"}

		if ((player.baseHealth >= 160000 or player.baseMagic >= 80000) and player.mark == 0) then
			table.insert(options, "Il San")
		elseif ((player.baseHealth >= 320000 or player.baseMagic >= 160000) and player.mark == 1) then
			table.insert(options, "Ee San")
		elseif ((player.baseHealth >= 640000 or player.baseMagic >= 320000) and player.mark == 2) then
			--table.insert(options,"Sam San")
		elseif ((player.baseHealth >= 1280000 or player.baseMagic >= 640000) and player.mark == 3) then
			--table.insert(options,"Sa San")
		elseif ((player.baseHealth >= 2560000 or player.baseMagic >= 1280000) and player.mark == 4) then
			--table.insert(options,"Oh San")
		end

		if player.m ~= npc.m then
			return
		end

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			options
		)

		if choice == "Shadow Stats" then
			ExpSellerNpc.showShadowStatsMenu(player, npc)
		elseif choice == "Bon-Hwa Immortality" then
			if player.m ~= npc.m then
				return
			end

			local options2 = {"Senjataku"}

			local choice2 = player:menuString(
				"Apa yang ingin kau jampi?",
				options2
			)

			if choice2 == "Senjataku" then
				local availableItems = {}
				local pathItems = {}
				local subpathItems = {}

				--- base paths ---
				if player.baseClass == 1 then
					pathItems = {
						"spike",
						"enchanted_spike",
						"il_san_spike",
						"ee_san_spike",
						"sam_san_spike",
						"sa_san_spike"
					}
				elseif player.baseClass == 2 then
					pathItems = {
						"blood",
						"enchanted_blood",
						"il_san_blood",
						"ee_san_blood",
						"sam_san_blood",
						"sa_san_blood"
					}
				elseif player.baseClass == 3 then
					pathItems = {
						"surge",
						"enchanted_surge",
						"il_san_surge",
						"ee_san_surge",
						"sam_san_surge",
						"sa_san_surge"
					}
				elseif player.baseClass == 4 then
					pathItems = {
						"charm",
						"enchanted_charm",
						"il_san_charm",
						"ee_san_charm",
						"sam_san_charge",
						"sa_san_charge"
					}
				end

				for i = 1, #pathItems do
					table.insert(availableItems, pathItems[i])
				end

				if subpathItems ~= nil then
					for i = 1, #subpathItems do
						table.insert(availableItems, subpathItems[i])
					end
				end

				local itemChoice = player:sell(
					"Please select the weapon you would like to enchant",
					availableItems
				)
				local selection = player:getInventoryItem(itemChoice - 1)
				local selected_item = {
					name = selection.name,
					yname = selection.yname
				}

				local currentItemLevel = 1
				local maxItemLevel = 1
				local maxItemLevelString = ""
				local baseItemLevelString = ""

				if player.mark == 0 and (player.baseHealth >= 80000 or player.baseMagic >= 40000) then
					-- enchanted
					maxItemLevel = 2
					maxItemLevelString = "enchanted"
				elseif player.mark == 1 then
					maxItemLevel = 3
					maxItemLevelString = "il_san"
				elseif player.mark == 2 then
					maxItemLevel = 4
					maxItemLevelString = "ee_san"
				elseif player.mark == 3 then
					maxItemLevel = 5
					maxItemLevelString = "sam_san"
				elseif player.mark == 4 then
					maxItemLevel = 6
					maxItemLevelString = "sa_san"
				end

				for i = 1, #pathItems do
					if selection.yname == pathItems[i] then
						currentItemLevel = i
						baseItemLevelString = pathItems[1]
						break
					end
				end

				if subpathItems ~= nil then
					for i = 1, #subpathItems do
						if selection.yname == subpathItems[i] then
							currentItemLevel = i
							baseItemLevelString = subpathItems[1]
						end
					end
				end

				if currentItemLevel >= maxItemLevel then
					player:dialogSeq(
						{
							t,
							"" .. selected_item.name .. " milikmu sudah pada tingkat tertinggi dan tidak bisa ditingkatkan lagi."
						},
						0
					)
					return
				end

				local choice3 = player:menuSeq(
					"Biayanya " .. Tools.formatNumber(200000000) .. " pengalaman untuk menyihir " .. selected_item.name .. " sampai tandamu sekarang.\n\nMau kau tingkatkan barang ini?",
					{"Okay", "Tidak"},
					{}
				)

				if choice3 == 1 then
					-- accept
					if player.exp < 200000000 then
						player:dialogSeq(
							{t, "Pengalamanmu tidak cukup."},
							0
						)
						return
					end

					player.exp = player.exp - 200000000
					player:sendStatus()
					player:removeItemSlot(itemChoice - 1, 1)
					player:addItem(
						maxItemLevelString .. "_" .. baseItemLevelString,
						1,
						0,
						player.ID
					)
					player:dialogSeq({t, "Pakailah senjata ini dengan baik dan bijak."}, 0)
				elseif choice3 == 1 then
					-- no
					player:dialogSeq(
						{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
						0
					)
				end
			end
		elseif choice == "Il San" then
			local options = {}

			if player:hasLegend("passed_first_trial_of_knowledge") and player:hasLegend("passed_first_trial_of_strength") and player:hasLegend("passed_first_trial_of_wealth") then
				local anim

				if player.baseClass == 1 then
					anim = 83
				elseif player.baseClass == 2 then
					anim = 82
				elseif player.baseClass == 3 then
					anim = 81
				elseif player.baseClass == 4 then
					anim = 80
				end

				player:sendAnimation(anim, 5)

				player:removeLegendbyName("passed_first_trial_of_knowledge")
				player:removeLegendbyName("passed_first_trial_of_strength")
				player:removeLegendbyName("passed_first_trial_of_wealth")

				player:addLegend(
					"Attained First Mark (" .. curT() .. ")",
					"attained_first_mark",
					32,
					15
				)
				player:updatePath(player.class, 1)
				characterLog.genericWrite(player, "Attained First Mark")
				broadcast(
					-1,
					"[SYSTEM]: " .. player.name .. " has attained First Mark!"
				)
			end

			if not player:hasLegend("passed_first_trial_of_knowledge") then
				table.insert(options, "Ujian Pertama Pengetahuan")
			end
			if not player:hasLegend("passed_first_trial_of_strength") then
				table.insert(options, "Ujian Pertama Kekuatan")
			end
			if not player:hasLegend("passed_first_trial_of_wealth") then
				table.insert(options, "Ujian Pertama Kekayaan")
			end

			local choice2 = player:menuString("Pilih satu ujian.", options)

			if choice2 == "Ujian Pertama Pengetahuan" then
				local choice3 = player:menuSeq(
					"Untuk menuntaskan ujian ini, kau harus mengorbankan 1.200.000.000 (1,2 miliar) pengalaman kepadaku. Kau bersedia?",
					{"Ya", "Tidak"},
					{}
				)

				if choice3 == 1 then
					-- yes
					if player.exp < 1200000000 then
						player:dialogSeq(
							{t, "Kembalilah kalau pengalamanmu sudah cukup."},
							0
						)
						return
					end

					player.exp = player.exp - 1200000000
					player:sendStatus()

					player:addLegend(
						"Lulus Ujian Pertama Pengetahuan (" .. curT() .. ")",
						"passed_first_trial_of_knowledge",
						3,
						15
					)

					player:dialogSeq(
						{
							t,
							"Selamat! Kau telah menuntaskan Ujian Pertama Pengetahuan."
						},
						0
					)
				elseif choice3 == 2 then
					-- no
					player:dialogSeq({t, "Kembalilah kalau kau sudah serius."}, 0)
				end
			elseif choice2 == "Ujian Pertama Kekuatan" then
				player:dialogSeq(
					{
						t,
						"Untuk ujian ini kau harus membunuh Spirit Rat menyebalkan yang berkeliaran di Rat Cave. Kembalilah kepadaku setelah selesai."
					},
					1
				)

				if player:killCount("spirit_rat") >= 1 then
					-- killed spirit rat
					player:addLegend(
						"Lulus Ujian Pertama Kekuatan (" .. curT() .. ")",
						"passed_first_trial_of_strength",
						1,
						15
					)
					player:dialogSeq(
						{
							t,
							"Selamat! Kau telah menuntaskan Ujian Pertama Kekuatan."
						},
						0
					)
				else
					player:dialogSeq(
						{t, "Kau masih harus membunuh Spirit Rat."},
						0
					)
					return
				end
			elseif choice2 == "Ujian Pertama Kekayaan" then
				local choice3 = player:menuSeq(
					"Untuk menuntaskan ujian ini, kau harus mengorbankan 600.000 keping dan 5 Well Crafted White amber kepadaku. Kau bersedia?",
					{"Ya", "Tidak"},
					{}
				)

				if choice3 == 1 then
					--yes
					if player.money < 600000 then
						player:dialogSeq(
							{t, "Temui aku lagi kalau emasnya sudah kau punya."},
							0
						)
						return
					end

					if player:hasItem("well_crafted_white_amber", 5) ~= true then
						player:dialogSeq(
							{
								t,
								"Kembalilah kepadaku kalau 5 well crafted white amber-nya sudah kau punya."
							},
							0
						)
						return
					end

					player:removeGold(600000)
					player:removeItem("well_crafted_white_amber", 5)
					player:addLegend(
						"Lulus Ujian Pertama Kekayaan (" .. curT() .. ")",
						"passed_first_trial_of_wealth",
						1,
						15
					)
				elseif choice3 == 2 then
					--no
					player:dialogSeq({t, "Kembalilah kalau kau sudah serius."}, 0)
					return
				end
			end
		elseif choice == "Ee San" then
			local options = {}

			if player:hasLegend("passed_second_trial_of_knowledge") and player:hasLegend("passed_second_trial_of_strength") and player:hasLegend("passed_second_trial_of_wealth") and player:hasLegend("passed_second_trial_of_skill") and player:hasLegend("passed_second_trial_of_culture") and player:hasLegend("passed_second_trial_of_spirit") then
				local anim

				if player.baseClass == 1 then
					anim = 83
				elseif player.baseClass == 2 then
					anim = 82
				elseif player.baseClass == 3 then
					anim = 81
				elseif player.baseClass == 4 then
					anim = 80
				end

				player:sendAnimation(anim, 5)

				player:removeLegendbyName("passed_second_trial_of_knowledge")
				player:removeLegendbyName("passed_second_trial_of_strength")
				player:removeLegendbyName("passed_second_trial_of_wealth")
				player:removeLegendbyName("passed_second_trial_of_skill")
				player:removeLegendbyName("passed_second_trial_of_culture")
				player:removeLegendbyName("passed_second_trial_of_spirit")

				player:addLegend(
					"Attained Second Mark (" .. curT() .. ")",
					"attained_second_mark",
					33,
					15
				)
				player:updatePath(player.class, 2)
				characterLog.genericWrite(player, "Attained Second Mark")
				broadcast(
					-1,
					"[SYSTEM]: " .. player.name .. " has attained Second Mark!"
				)
			end

			if not player:hasLegend("passed_second_trial_of_knowledge") then
				table.insert(options, "Ujian Kedua Pengetahuan")
			end
			if not player:hasLegend("passed_second_trial_of_strength") then
				table.insert(options, "Ujian Kedua Kekuatan")
			end
			if not player:hasLegend("passed_second_trial_of_wealth") then
				table.insert(options, "Ujian Kedua Kekayaan")
			end
			if not player:hasLegend("passed_second_trial_of_skill") then
				table.insert(options, "Ujian Kedua Keahlian")
			end
			if not player:hasLegend("passed_second_trial_of_culture") then
				table.insert(options, "Ujian Kedua Kebudayaan")
			end
			if not player:hasLegend("passed_second_trial_of_spirit") then
				table.insert(options, "Ujian Kedua Jiwa")
			end

			local choice2 = player:menuString("Pilih satu ujian.", options)

			if choice2 == "Ujian Kedua Pengetahuan" then
				local choice3 = player:menuSeq(
					"Untuk menuntaskan ujian ini, kau harus mengorbankan 1.200.000.000 (1,2 miliar) pengalaman kepadaku. Kau bersedia?",
					{"Ya", "Tidak"},
					{}
				)

				if choice3 == 1 then
					-- yes
					if player.exp < 1200000000 then
						player:dialogSeq(
							{t, "Kembalilah kalau pengalamanmu sudah cukup."},
							0
						)
						return
					end

					player.exp = player.exp - 1200000000
					player:sendStatus()

					player:addLegend(
						"Lulus Ujian Kedua Pengetahuan (" .. curT() .. ")",
						"passed_second_trial_of_knowledge",
						3,
						15
					)

					player:dialogSeq(
						{
							t,
							"Selamat! Kau telah menuntaskan Ujian Kedua Pengetahuan."
						},
						0
					)
				elseif choice3 == 2 then
					-- no
					player:dialogSeq({t, "Kembalilah kalau kau sudah serius."}, 0)
				end
			elseif choice2 == "Ujian Kedua Kebudayaan" then
				player:dialogSeq(
					{
						t,
						"Untuk ujian ini kau harus mencapai tingkat Skilled atau lebih tinggi pada salah satu dari Tailoring, Metalworking, Jewelcrafting, atau Carpentry"
					},
					1
				)

				if crafting.checkSkillLevel(player, "tailoring", "skilled") or crafting.checkSkillLevel(
					player,
					"metalworking",
					"skilled"
				) or crafting.checkSkillLevel(player, "woodworking", "skilled") or crafting.checkSkillLevel(
					player,
					"jewelry making",
					"skilled"
				) then
					player:addLegend(
						"Lulus Ujian Kedua Kebudayaan (" .. curT() .. ")",
						"passed_second_trial_of_culture",
						3,
						15
					)
					player:dialogSeq(
						{
							t,
							"Selamat! Kau telah menuntaskan Ujian Kedua Kebudayaan."
						},
						0
					)
					return
				else
					player:dialogSeq(
						{
							t,
							"Kau belum mencapai tingkat Skilled pada Tailoring, Metalworking, Jewelcrafting, maupun Carpentry. Kembalilah kepadaku kalau sudah."
						},
						0
					)
					return
				end
			elseif choice2 == "Ujian Kedua Jiwa" then
				if not player:karmaCheck("tiger") then
					player:dialogSeq(
						{
							t,
							"Kau harus punya karma Tiger atau lebih baik untuk lulus Ujian Kedua Jiwa"
						},
						0
					)
					return
				end

				player:addLegend(
					"Lulus Ujian Kedua Jiwa (" .. curT() .. ")",
					"passed_second_trial_of_spirit",
					3,
					15
				)
				player:dialogSeq(
					{
						t,
						"Selamat! Kau telah menuntaskan Ujian Kedua Jiwa."
					},
					0
				)
			elseif choice2 == "Ujian Kedua Keahlian" then
				local passed = false
				player:dialogSeq(
					{
						t,
						"Untuk ujian ini kau harus meraih sedikitnya 6 kemenangan carnage atau 12 kemenangan minigame secara keseluruhan. Kembalilah kepadaku setelah selesai."
					},
					1
				)

				if player.registry["carnageWin"] >= 6 then
					passed = true
				end

				if player.registry["carnageWin"] + player.registry["elixir_war_victories"] + player.registry[
					"bomber_war_wins"
				] + player.registry["beach_war_wins"] + player.registry[
					"sumo_war_wins"
				] >= 12 then
					passed = true
				end

				if passed == false then
					player:dialogSeq(
						{
							t,
							"Kemenanganmu belum cukup untuk memuaskan hasrat darahku. Kembalilah kalau sudah."
						},
						0
					)
					return
				end

				player:addLegend(
					"Lulus Ujian Kedua Keahlian (" .. curT() .. ")",
					"passed_second_trial_of_skill",
					1,
					15
				)
				player:dialogSeq(
					{
						t,
						"Selamat! Kau telah menuntaskan Ujian Kedua Keahlian."
					},
					0
				)
			elseif choice2 == "Ujian Kedua Kekuatan" then
				local mobs1 = player:allMythicCaveBosses("rabbit")
				local mobs2 = player:allMythicCaveBosses("monkey")
				local mobs3 = player:allMythicCaveBosses("dog")
				local mobs4 = player:allMythicCaveBosses("rooster")
				local mobs5 = player:allMythicCaveBosses("rat")
				local mobs6 = player:allMythicCaveBosses("ox")
				local mobs7 = player:allMythicCaveBosses("horse")
				local mobs8 = player:allMythicCaveBosses("pig")

				local quest = "second_trial_of_strength"

				if player.quest["second_trial_of_strength"] == 0 then
					player:dialogSeq(
						{
							t,
							"Untuk ujian ini kau harus membunuh bos Spirit dan Avenger di gua Mythic, kecuali Tiger, Dragon, Sheep, dan Snake. Kembalilah kepadaku setelah selesai."
						},
						1
					)

					player.quest[quest] = 1

					--[[player:setQuestKillCounts(quest,mobs1)
						player:setQuestKillCounts(quest,mobs2)
						player:setQuestKillCounts(quest,mobs3)
						player:setQuestKillCounts(quest,mobs4)
						player:setQuestKillCounts(quest,mobs5)
						player:setQuestKillCounts(quest,mobs6)
						player:setQuestKillCounts(quest,mobs7)
						player:setQuestKillCounts(quest,mobs8)]]
					--
				elseif player.quest[quest] == 1 then
					local count = 0
					local diff = 1

					if player:killedEnough(mobs1, diff) then
						count = count + 1
					end
					if player:killedEnough(mobs2, diff) then
						count = count + 1
					end
					if player:killedEnough(mobs3, diff) then
						count = count + 1
					end
					if player:killedEnough(mobs4, diff) then
						count = count + 1
					end
					if player:killedEnough(mobs5, diff) then
						count = count + 1
					end
					if player:killedEnough(mobs6, diff) then
						count = count + 1
					end
					if player:killedEnough(mobs7, diff) then
						count = count + 1
					end
					if player:killedEnough(mobs8, diff) then
						count = count + 1
					end

					if count < 8 then
						player:dialogSeq(
							{
								t,
								"Kau tidak mengindahkan kata-kataku dan tidak cukup banyak membunuh untuk memenuhi dendamku!"
							},
							0
						)
						return
					end

					-- these functions will need disabled in future, let run for 2 months from 07/18/19
					player:clearQuestKillCounts(quest, mobs1)
					player:clearQuestKillCounts(quest, mobs2)
					player:clearQuestKillCounts(quest, mobs3)
					player:clearQuestKillCounts(quest, mobs4)
					player:clearQuestKillCounts(quest, mobs5)
					player:clearQuestKillCounts(quest, mobs6)
					player:clearQuestKillCounts(quest, mobs7)
					player:clearQuestKillCounts(quest, mobs8)

					player:addLegend(
						"Lulus Ujian Kedua Kekuatan (" .. curT() .. ")",
						"passed_second_trial_of_strength",
						1,
						15
					)
					player:dialogSeq(
						{
							t,
							"Selamat! Kau telah menuntaskan Ujian Kedua Kekuatan."
						},
						0
					)
				end
			elseif choice2 == "Ujian Kedua Kekayaan" then
				local choice3 = player:menuSeq(
					"Untuk menuntaskan ujian ini, kau harus mengorbankan 2.000.000 emas kepadaku. Kau bersedia?",
					{"Ya", "Tidak"},
					{}
				)

				if choice3 == 1 then
					--yes
					if player.money < 2000000 then
						player:dialogSeq(
							{t, "Temui aku lagi kalau emasnya sudah kau punya."},
							0
						)
						return
					end

					player:removeGold(2000000)

					player:addLegend(
						"Lulus Ujian Kedua Kekayaan (" .. curT() .. ")",
						"passed_second_trial_of_wealth",
						7,
						15
					)
				elseif choice3 == 2 then
					--no
					player:dialogSeq({t, "Kembalilah kalau kau sudah serius."}, 0)
					return
				end
			end
		end
	end)
}
