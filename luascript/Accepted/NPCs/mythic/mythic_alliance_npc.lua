MythicAllianceNpc = {
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

		--[[
			Horse <> Rat
			Rooster <> Rabbit
			Snake <> Pig
			Dog <> Dragon
			Sheep <> Ox
			Tiger <> Monkey
		]]--

		local alliance = ""
		local enemy = ""
		local item1
		local item1_amount
		local item2
		local item2_amount

		if player.mapTitle == "Mythic Dragon" then
			alliance = "Dragon"
			enemy = "Dog"
			item1 = "fragile_rose"
			item1_amount = 4
			item2 = "key_to_wind"
			item2_amount = 8
		end
		if player.mapTitle == "Mythic Dog" then
			alliance = "Dog"
			enemy = "Dragon"
			item1 = "dragons_liver"
			item1_amount = 4
			item2 = "chung_ryong_key"
			item2_amount = 4
		end
		if player.mapTitle == "Mythic Rat" then
			alliance = "Rat"
			enemy = "Horse"
			item1 = "pearl_charm"
			item1_amount = 4
			item2 = "key_to_thunder"
			item2_amount = 8
		end
		if player.mapTitle == "Mythic Horse" then
			alliance = "Horse"
			enemy = "Rat"
			item1 = "battle_helm"
			item1_amount = 4
			item2 = "key_to_pond"
			item2_amount = 8
		end
		if player.mapTitle == "Mythic Rooster" then
			alliance = "Rooster"
			enemy = "Rabbit"
			item1 = "lucky_coin"
			item1_amount = 4
			item2 = "key_to_earth"
			item2_amount = 8
		end
		if player.mapTitle == "Mythic Rabbit" then
			alliance = "Rabbit"
			enemy = "Rooster"
			item1 = "scribes_pen"
			item1_amount = 4
			item2 = "key_to_heaven"
			item2_amount = 8
		end
		if player.mapTitle == "Mythic Snake" then
			alliance = "Snake"
			enemy = "Pig"
			item1 = "magical_dust"
			item1_amount = 4
			item2 = "key_to_mountain"
			item2_amount = 8
		end
		if player.mapTitle == "Mythic Pig" then
			alliance = "Pig"
			enemy = "Snake"
			item1 = "scribes_book"
			item1_amount = 4
			item2 = "hyun_moo_key"
			item2_amount = 4
		end
		if player.mapTitle == "Mythic Sheep" then
			alliance = "Sheep"
			enemy = "Ox"
			item1 = "tao_stone"
			item1_amount = 4
			item2 = "key_to_water"
			item2_amount = 8
		end
		if player.mapTitle == "Mythic Ox" then
			alliance = "Ox"
			enemy = "Sheep"
			item1 = "lucky_silver_coin"
			item1_amount = 4
			item2 = "ju_jak_key"
			item2_amount = 4
		end
		if player.mapTitle == "Mythic Tiger" then
			alliance = "Tiger"
			enemy = "Monkey"
			item1 = "ambrosia"
			item1_amount = 4
			item2 = "key_to_fire"
			item2_amount = 8
		end
		if player.mapTitle == "Mythic Monkey" then
			alliance = "Monkey"
			enemy = "Tiger"
			item1 = "purified_water"
			item1_amount = 4
			item2 = "baekho_key"
			item2_amount = 4
		end

		if speech == string.lower(enemy) then
			local mobs = player:allMythicCaveBosses(string.lower(enemy))

			Tools.checkKarma(player)

			if player:hasLegend("lesser_alliance_" .. string.lower(enemy)) then
				stormstrike.cast(npc, player)
				player:returnToInn()
				player:dialogSeq(
					{t, "Kau bersekutu dengan musuh kami. Mampus, sampah!"},
					0
				)
				return
			end
			if player:hasLegend("lesser_alliance_" .. string.lower(alliance)) or player:hasLegend("greater_alliance_" .. string.lower(alliance)) then
				rebirth.cast(npc, player)
				return
			end

			local items = "(" .. item1_amount .. ") " .. Item(item1).name .. " and (" .. item2_amount .. ") " .. Item(item2).name

			if player.quest["lesser_alliance_" .. string.lower(alliance)] == 1 then
				-- already on quest

				local killedEnough = false

				if ((player:killCount(mobs[1]) >= 3 and player:killCount(mobs[2]) >= 3) or (player:killCount(mobs[3]) >= 3 and player:killCount(mobs[4]) >= 3) or (player:killCount(mobs[5]) >= 3 and player:killCount(mobs[6]) >= 3)) then
					killedEnough = true
				end

				if killedEnough == false then
					player:dialogSeq(
						{
							t,
							"Kau tidak mengindahkan kata-kataku dan tidak cukup banyak membunuh untuk memenuhi dendamku!"
						},
						0
					)
					return
				end

				if player:hasItem(item1, item1_amount) ~= true or player:hasItem(
					item2,
					item2_amount
				) ~= true then
					player:dialogSeq(
						{t, "Barang yang diperlukan belum lengkap!"},
						0
					)
					return
				end

				player:removeItem(item1, item1_amount)
				player:removeItem(item2, item2_amount)

				player:addKarma(4)
				player:addItem(alliance .. "s_favor", 1)
				player:giveXP(10000000)
				player:addLegend(
					"Persekutuan kecil dengan " .. alliance .. " (" .. curT() .. ")",
					"lesser_alliance_" .. string.lower(alliance),
					5,
					128
				)
				player.quest["lesser_alliance_" .. string.lower(alliance)] = 0
				player:dialogSeq(
					{
						t,
						"Kau telah membuktikan kelayakanmu! Anggaplah dirimu sekutu " .. alliance .. "!"
					},
					1
				)

				return
			end

			local choice = player:menuSeq(
				"Salam, manusia fana. Waktunya tepat. Maukah kau bersekutu dengan yang perkasa " .. alliance .. "?",
				{"Dengan hormat.", "Aku menahan kesetiaanku."},
				{}
			)

			if choice == 1 then
				-- accept

				local confirm = player:menuSeq(
					"Memulai tugas ini akan menolkan hitungan bunuhmu atas mob-mob ini. Lanjutkan?",
					{"Ya, nolkan hitungan bunuhnya.", "Tidak, lupakan saja."},
					{}
				)

				if confirm == 1 then
					local quest = "Started lesser alliance quest for " .. alliance .. "\n"
					quest = quest .. "Mob count before flush:\n"
					for i = 1, #mobs do
						quest = quest .. mobs[i] .. " count: " .. player:killCount(mobs[i]) .. "\n"
					end

					characterLog.questWrite(player, quest)

					for i = 1, #mobs do
						-- flushes mobs at accepting of quest
						player:flushKills(mobs[i])
					end

					player.quest["lesser_alliance_" .. string.lower(alliance)] = 1
					player:dialogSeq(
						{
							t,
							"Pilihan yang bijak. Kami berjuang baik dalam pertikaian abadi melawan si keji " .. enemy .. ". Kutugaskan kau membantu kami menghabisi mereka!"
						},
						1
					)
					player:dialogSeq(
						{
							t,
							"Bunuh tiga dari tiap pemimpin mereka dan bawakan aku " .. items .. ". Jangan sampai terlalu terpecah perhatian, kami ingin menang! Aku ingin darah " .. enemy .. " masih segar di bilahmu!"
						},
						1
					)
				end
			elseif choice == 2 then
				stormstrike.cast(npc, player)
				player:dialogSeq({t, "Kalau begitu, matilah."}, 0)
				return
			end
		elseif speech == "agung" or speech == "aliansi agung" then
			Tools.checkKarma(player)

			if player:hasLegend("greater_alliance_" .. string.lower(alliance)) then
				rebirth.cast(npc, player)
				return
			end
			if player:hasLegend("lesser_alliance_" .. string.lower(enemy)) then
				stormstrike.cast(npc, player)
				player:returnToInn()
				player:dialogSeq(
					{t, "Kau bersekutu dengan musuh kami. Mampus, sampah!"},
					0
				)
				return
			end
			if not player:hasLegend("lesser_alliance_" .. string.lower(alliance)) then
				player:dialogSeq({t, "Kau bukan sekutu kami."}, 0)
				return
			end

			local alliances = {
				"dragon",
				"dog",
				"rat",
				"horse",
				"rooster",
				"rabbit",
				"snake",
				"pig",
				"sheep",
				"ox",
				"tiger",
				"monkey"
			}
			local alliance_count = 0

			for i = 1, #alliances do
				if player:hasLegend("lesser_alliance_" .. alliances[i]) then
					alliance_count = alliance_count + 1
				end
				if player:hasLegend("greater_alliance_" .. alliances[i]) then
					alliance_count = alliance_count + 1
				end
			end

			if alliance_count < 6 then
				player:sendMinitext("" .. alliance_count)
				player:dialogSeq(
					{
						t,
						"Kau harus menuntaskan lebih banyak persekutuan sebelum memulai perjalanan ini."
					},
					0
				)
				return
			end

			local enemy1, enemy2, enemy3 = player:allEnemyMythicCaveBosses(alliance)

			-- this guarantees one set for the specific cave
			local mobs1 = player:allMythicCaveBosses(string.lower(enemy1))
			local mobs2 = player:allMythicCaveBosses(string.lower(enemy2))
			local mobs3 = player:allMythicCaveBosses(string.lower(enemy3))

			if player.quest["greater_alliance_" .. string.lower(alliance)] == 1 then
				-- already on quest
				local count = 0

				if player:killedEnough(mobs1, 5) then
					count = count + 1
				end
				if player:killedEnough(mobs2, 5) then
					count = count + 1
				end
				if player:killedEnough(mobs3, 5) then
					count = count + 1
				end

				if count < 3 then
					player:dialogSeq(
						{
							t,
							"Kau gagal membunuh semua yang kuminta. Bagaimana aku bisa memercayaimu kalau kau tidak mendengarkan?"
						},
						0
					)
					return
				end

				-- Remove clearQuestkills 2 months from 07/18/19
				player:clearQuestKillCounts("greater_alliance", mobs1)
				player:clearQuestKillCounts("greater_alliance", mobs2)
				player:clearQuestKillCounts("greater_alliance", mobs3)

				player:addKarma(8)
				player:addLegend(
					"Persekutuan besar dengan " .. alliance .. " (" .. curT() .. ")",
					"greater_alliance_" .. string.lower(alliance),
					5,
					128
				)
				player:removeLegendbyName("lesser_alliance_" .. string.lower(alliance))
				player.quest["greater_alliance_" .. string.lower(alliance)] = 0
				player:dialogSeq(
					{
						t,
						"Kau telah membuktikan kelayakanmu! Anggaplah dirimu sekutu perkasa " .. alliance .. "!"
					},
					1
				)
				return
			end

			if player.mark == 0 then
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau pengalamanmu lebih banyak."},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Musuh kami, " .. enemy .. " telah mendapat kawan dalam perang melawan kami.",
					"Kutugaskan kau, juaraku, mengambil alih perkara ini dan memutus persekutuan mereka."
				},
				1
			)

			local choice = player:menuSeq(
				"Maukah kau membantu kami?",
				{"Ya, aku mau", "Tidak, aku tidak mau"},
				{}
			)

			if choice == 1 then
				-- accept

				local count = 0
				for i = 1, #alliances do
					if player:hasLegend("greater_alliance_" .. string.lower(alliances[i])) then
						count = count + 1
					end
				end

				for i = 1, #alliances do
					if player.quest["greater_alliance_" .. string.lower(alliances[i])] == 1 then
						-- this checks to make sure person is only doing one GA at a time
						if alliances[i] == string.lower(enemy) then
							stormstrike.cast(npc, player)
							player:returnToInn()
							player:dialogSeq(
								{t, "Kau membantu musuh kami!"},
								0
							)
							return
						else
							player:dialogSeq(
								{
									t,
									"Kau sedang menjalani tugas Persekutuan besar yang lain"
								},
								0
							)
							return
						end
					end
				end

				if count > 3 then
					player:dialogSeq(
						{
							t,
							"Kau sudah menuntaskan tiga Persekutuan besar."
						},
						0
					)
					return
				end

				local quest = "Started Greater alliance quest for " .. alliance .. "\n"
				quest = quest .. "Mob count before flush:\n"
				for i = 1, #mobs1 do
					quest = quest .. mobs1[i] .. " count: " .. player:killCount(mobs1[i]) .. "\n"
				end
				for i = 1, #mobs2 do
					quest = quest .. mobs2[i] .. " count: " .. player:killCount(mobs2[i]) .. "\n"
				end
				for i = 1, #mobs3 do
					quest = quest .. mobs3[i] .. " count: " .. player:killCount(mobs3[i]) .. "\n"
				end

				characterLog.questWrite(player, quest)

				--player:setQuestKillCounts("greater_alliance",mobs1)
				--player:setQuestKillCounts("greater_alliance",mobs2)
				--player:setQuestKillCounts("greater_alliance",mobs3)

				player.quest["greater_alliance_" .. string.lower(alliance)] = 1
				player:dialogSeq(
					{
						t,
						"" .. enemy2 .. " telah memasok perbekalan kepada musuh kita. Bunuh 5 dari tiap pemimpin mereka.",
						"" .. enemy3 .. " telah memasok orang kepada musuh kita. Bunuh juga 5 dari tiap pemimpin mereka.",
						"Terakhir, pukullah sekali lagi demi kaum kami dan bunuh 5 " .. enemy1 .. " bosses again.",
						"Segera kembali kepadaku. Ini akan meremukkan semangat mereka!"
					},
					1
				)
			elseif choice == 2 then
				stormstrike.cast(npc, player)
				player:dialogSeq({t, "Kalau begitu, matilah."}, 0)
				return
			end
		end
	end)
}
