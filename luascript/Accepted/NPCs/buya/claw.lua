ClawNpc = {
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

		if speech == "aku kehilangan surat harimauku" then
			player:dialogSeq(
				{
					t,
					"Oh? Jadi tiger mail-mu hilang? Aku harus mengajarimu ulang jalan sang harimau."
				},
				1
			)

			local choice = player:menuSeq(
				"Kau yakin ingin mempelajari ulang jalan sang harimau? (ini akan menolkan kemajuan tugasmu)",
				{"Ya", "Tidak"},
				{}
			)

			if choice == 1 then
				player.quest["tiger_armor"] = 0
				player:dialogSeq(
					{
						t,
						"Ah, jadi kita akan mulai lagi. Ucapkan \"Chongun\" kalau kau siap memulai kembali."
					},
					0
				)
				return
			end
			return
		end

		if speech == "chongun" then
			Tools.checkKarma(player)

			if player.baseClass ~= 1 then
				player:dialogSeq({t, "Maaf, aku tidak bisa menolong kaummu."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Di kehidupan-kehidupan sebelumnya, aku Chongun yang perkasa, yang akan membuat orang-orang sezamanmu malu.",
					"Kau mencari sariku? Kau ingin menjadi sekuat dan setangguh sang harimau?",
					"Kalau begitu kau harus memakai sari yang ada dalam dirimu. JANGAN serahkan apa pun kepadaku. Cukup bawa saja."
				},
				1
			)

			if player.quest["tiger_armor"] == 0 then
				-- just starting out
				if player.level < 5 then
					player:dialogSeq(
						{t, "Kembalilah kalau kau sudah mencapai level 5."},
						0
					)
					return
				end

				local neededItems = {}
				local neededItemAmounts = {}

				if player.sex == 0 then
					neededItems = {Item("antler"), Item("war_platemail")}
					neededItemAmounts = {1, 1}
					armor = Item("tiger_mail")
				elseif player.sex == 1 then
					neededItems = {Item("antler"), Item("spring_war_dress")}
					neededItemAmounts = {1, 1}
					armor = Item("tigress")
				end

				if player:hasItem(neededItems[1].yname, neededItemAmounts[1]) ~= true or player:hasItem(neededItems[2].yname, neededItemAmounts[2]) ~= true then
					player:dialogSeq(
						{
							t,
							"Ambilkan " .. neededItems[1].name .. " dan " .. neededItems[
								2
							].name
						},
						0
					)
					return
				end

				player:removeItem(neededItems[1].yname, neededItemAmounts[1], 9)
				player:removeItem(neededItems[2].yname, neededItemAmounts[2], 9)

				player:addItem(armor.yname, 1)

				local exp = player.exp - 664
				if exp < 0 then
					exp = 0
					player.exp = exp
				end
				player:sendStatus()

				player.quest["tiger_armor"] = 10
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah mencapai level 10."},
					0
				)
			end

			if player.quest["tiger_armor"] == 10 then
				-- level 10 quest
				if player.level < 10 then
					player:dialogSeq(
						{t, "Kembalilah kalau kau sudah mencapai level 10."},
						0
					)
					return
				end

				local neededItems = {}
				local neededItemAmounts = {}

				if player.sex == 0 then
					neededItems = {
						Item("gold_acorn"),
						Item("jade_war_platemail"),
						Item("tiger_mail")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("jade_tiger_mail")
				elseif player.sex == 1 then
					neededItems = {
						Item("gold_acorn"),
						Item("summer_war_dress"),
						Item("tigress")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("jade_tigress")
				end

				if (player:hasItem(neededItems[1].yname, neededItemAmounts[1]) ~= true or
					player:hasItem(neededItems[2].yname, neededItemAmounts[2]) ~= true or
					player:hasItem(neededItems[3].yname, neededItemAmounts[3]) ~= true) then
					
					player:dialogSeq(
						{
							t,
							"Untuk zirah berikutnya, bawakan " .. neededItems[1].name .. ", " .. neededItems[
								2
							].name .. ", dan " .. neededItems[3].name
						},
						0
					)
					return
				end

				player:removeItem(neededItems[1].yname, neededItemAmounts[1], 9)
				player:removeItem(neededItems[2].yname, neededItemAmounts[2], 9)
				player:removeItem(neededItems[3].yname, neededItemAmounts[3], 9)

				player:addItem(armor.yname, 1)

				local exp = player.exp - 2556
				if exp < 0 then
					exp = 0
					player.exp = exp
				end
				player:sendStatus()

				player.quest["tiger_armor"] = 20
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah mencapai level 20."},
					0
				)
			end

			if player.quest["tiger_armor"] == 20 then
				-- level 20 quest
				if player.level < 20 then
					player:dialogSeq(
						{t, "Kembalilah kalau kau sudah mencapai level 20."},
						0
					)
					return
				end

				local neededItems = {}
				local neededItemAmounts = {}

				if player.sex == 0 then
					neededItems = {
						Item("fox_blade"),
						Item("royal_war_platemail"),
						Item("jade_tiger_mail")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("royal_tiger_mail")
				elseif player.sex == 1 then
					neededItems = {
						Item("fox_blade"),
						Item("autumn_war_dress"),
						Item("jade_tigress")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("royal_tigress")
				end

				if player:hasItem(neededItems[1].yname, neededItemAmounts[1]) ~= true or player:hasItem(
					neededItems[2].yname,
					neededItemAmounts[2]
				) ~= true or player:hasItem(
					neededItems[3].yname,
					neededItemAmounts[3]
				) ~= true then
					player:dialogSeq(
						{
							t,
							"Untuk zirah berikutnya, bawakan " .. neededItems[1].name .. ", " .. neededItems[
								2
							].name .. ", dan " .. neededItems[3].name
						},
						0
					)
					return
				end

				player:removeItem(neededItems[1].yname, neededItemAmounts[1], 9)
				player:removeItem(neededItems[2].yname, neededItemAmounts[2], 9)
				player:removeItem(neededItems[3].yname, neededItemAmounts[3], 9)

				player:addItem(armor.yname, 1)

				local exp = player.exp - 11200
				if exp < 0 then
					exp = 0
					player.exp = exp
				end
				player:sendStatus()

				player.quest["tiger_armor"] = 30
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah mencapai level 30."},
					0
				)
			end

			if player.quest["tiger_armor"] == 30 then
				-- level 30 quest
				if player.level < 30 then
					player:dialogSeq(
						{t, "Kembalilah kalau kau sudah mencapai level 30."},
						0
					)
					return
				end

				local neededItems = {}
				local neededItemAmounts = {}

				if player.sex == 0 then
					neededItems = {
						Item("amber"),
						Item("sky_war_platemail"),
						Item("royal_tiger_mail")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("sky_tiger_mail")
				elseif player.sex == 1 then
					neededItems = {
						Item("amber"),
						Item("winter_war_dress"),
						Item("royal_tigress")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("sky_tigress")
				end

				if player:hasItem(neededItems[1].yname, neededItemAmounts[1]) ~= true or player:hasItem(
					neededItems[2].yname,
					neededItemAmounts[2]
				) ~= true or player:hasItem(
					neededItems[3].yname,
					neededItemAmounts[3]
				) ~= true then
					player:dialogSeq(
						{
							t,
							"Untuk zirah berikutnya, bawakan " .. neededItems[1].name .. ", " .. neededItems[
								2
							].name .. ", dan " .. neededItems[3].name
						},
						0
					)
					return
				end

				player:removeItem(neededItems[1].yname, neededItemAmounts[1], 9)
				player:removeItem(neededItems[2].yname, neededItemAmounts[2], 9)
				player:removeItem(neededItems[3].yname, neededItemAmounts[3], 9)

				player:addItem(armor.yname, 1)

				local exp = player.exp - 34784
				if exp < 0 then
					exp = 0
					player.exp = exp
				end
				player:sendStatus()

				player.quest["tiger_armor"] = 40
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah mencapai level 40."},
					0
				)
			end

			if player.quest["tiger_armor"] == 40 then
				-- level 40 quest
				if player.level < 40 then
					player:dialogSeq(
						{t, "Kembalilah kalau kau sudah mencapai level 40."},
						0
					)
					return
				end

				local neededItems = {}
				local neededItemAmounts = {}

				if player.sex == 0 then
					neededItems = {
						Item("moonblade"),
						Item("ancient_war_platemail"),
						Item("sky_tiger_mail")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("ancient_tiger_mail")
				elseif player.sex == 1 then
					neededItems = {
						Item("moonblade"),
						Item("ancient_war_dress"),
						Item("sky_tigress")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("ancient_tigress")
				end

				if player:hasItem(neededItems[1].yname, neededItemAmounts[1]) ~= true or player:hasItem(
					neededItems[2].yname,
					neededItemAmounts[2]
				) ~= true or player:hasItem(
					neededItems[3].yname,
					neededItemAmounts[3]
				) ~= true then
					player:dialogSeq(
						{
							t,
							"Untuk zirah berikutnya, bawakan " .. neededItems[1].name .. ", " .. neededItems[
								2
							].name .. ", dan " .. neededItems[3].name
						},
						0
					)
					return
				end

				player:removeItem(neededItems[1].yname, neededItemAmounts[1], 9)
				player:removeItem(neededItems[2].yname, neededItemAmounts[2], 9)
				player:removeItem(neededItems[3].yname, neededItemAmounts[3], 9)

				player:addItem(armor.yname, 1)

				local exp = player.exp - 70344
				if exp < 0 then
					exp = 0
					player.exp = exp
				end
				player:sendStatus()

				player.quest["tiger_armor"] = 50
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah mencapai level 50."},
					0
				)
			end

			if player.quest["tiger_armor"] == 50 then
				-- level 50 quest
				if player.level < 50 then
					player:dialogSeq(
						{t, "Kembalilah kalau kau sudah mencapai level 50."},
						0
					)
					return
				end

				local neededItems = {}
				local neededItemAmounts = {}

				if player.sex == 0 then
					neededItems = {
						Item("maxcaliber"),
						Item("blood_war_platemail"),
						Item("ancient_tiger_mail")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("blood_tiger_mail")
				elseif player.sex == 1 then
					neededItems = {
						Item("maxcaliber"),
						Item("blood_war_dress"),
						Item("ancient_tigress")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("blood_tigress")
				end

				if player:hasItem(neededItems[1].yname, neededItemAmounts[1]) ~= true or player:hasItem(
					neededItems[2].yname,
					neededItemAmounts[2]
				) ~= true or player:hasItem(
					neededItems[3].yname,
					neededItemAmounts[3]
				) ~= true then
					player:dialogSeq(
						{
							t,
							"Untuk zirah berikutnya, bawakan " .. neededItems[1].name .. ", " .. neededItems[
								2
							].name .. ", dan " .. neededItems[3].name
						},
						0
					)
					return
				end

				player:removeItem(neededItems[1].yname, neededItemAmounts[1], 9)
				player:removeItem(neededItems[2].yname, neededItemAmounts[2], 9)
				player:removeItem(neededItems[3].yname, neededItemAmounts[3], 9)

				player:addItem(armor.yname, 1)

				local exp = player.exp - 178032
				if exp < 0 then
					exp = 0
					player.exp = exp
				end
				player:sendStatus()

				player.quest["tiger_armor"] = 60
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah mencapai level 60."},
					0
				)
			end

			if player.quest["tiger_armor"] == 60 then
				-- level 60 quest
				if player.level < 60 then
					player:dialogSeq(
						{t, "Kembalilah kalau kau sudah mencapai level 60."},
						0
					)
					return
				end

				local neededItems = {}
				local neededItemAmounts = {}

				if player.sex == 0 then
					neededItems = {
						Item("electra"),
						Item("earth_war_platemail"),
						Item("blood_tiger_mail")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("earth_tiger_mail")
				elseif player.sex == 1 then
					neededItems = {
						Item("electra"),
						Item("earth_war_dress"),
						Item("blood_tigress")
					}
					neededItemAmounts = {1, 1, 1}
					armor = Item("earth_tigress")
				end

				if player:hasItem(neededItems[1].yname, neededItemAmounts[1]) ~= true or player:hasItem(
					neededItems[2].yname,
					neededItemAmounts[2]
				) ~= true or player:hasItem(
					neededItems[3].yname,
					neededItemAmounts[3]
				) ~= true then
					player:dialogSeq(
						{
							t,
							"Untuk zirah berikutnya, bawakan " .. neededItems[1].name .. ", " .. neededItems[
								2
							].name .. ", dan " .. neededItems[3].name
						},
						0
					)
					return
				end

				player:removeItem(neededItems[1].yname, neededItemAmounts[1], 9)
				player:removeItem(neededItems[2].yname, neededItemAmounts[2], 9)
				player:removeItem(neededItems[3].yname, neededItemAmounts[3], 9)

				player:addItem(armor.yname, 1)

				local exp = player.exp - 428544
				if exp < 0 then
					exp = 0
					player.exp = exp
				end
				player:sendStatus()

				player.quest["tiger_armor"] = 70
				player:dialogSeq(
					{
						t,
						"Kau sudah melihat semua yang kutahu, sebab aku hanya pernah hidup di bumi. Mungkin makhluk surgawi di tempat lain tahu lebih banyak."
					},
					0
				)
			end

			if player.quest["tiger_armor"] == 70 then
				player:dialogSeq(
					{
						t,
						"Kau sudah melihat semua yang kutahu, sebab aku hanya pernah hidup di bumi. Mungkin makhluk surgawi di tempat lain tahu lebih banyak."
					},
					0
				)
				return
			end
		end

		if player.level >= 99 then
			if speech == "naga" then
				player.quest["claw_soe"] = 1
				player:dialogSeq(
					{
						t,
						"Naga, katamu? Aku tahu sedikit tentang mereka, segala macam naga.",
						"Ada naga biasa, seperti yang kau temui di Mythic, lalu ada yang istimewa."
					},
					0
				)
			end

			if speech == "naga bumi" then
				player.quest["claw_soe"] = 2
				player:dialogSeq(
					{
						t,
						"Oh ya, naga Bumi. Dulu bangsa yang sangat perkasa, dan salah satu jenis naga yang abadi.",
						"Dulu ada ratusan ekor di dunia, dan mereka menyebarkan teror ke mana pun mereka pergi.",
						"Akhirnya kami mendapati kami tidak sanggup memusnahkan yang tersisa karena terlalu kuat, dan hanya bisa mengurung mereka di dalam serpihan."
					},
					0
				)
			end

			if speech == "serpihan" and player.quest["claw_soe"] >= 2 then
				player.quest["claw_soe"] = 3
				player:dialogSeq(
					{
						t,
						"Serpihan itu senjata rancangan Baegi. Kalau kau ingin tahu lebih banyak, tanyakan kepadanya."
					},
					0
				)
			end
		end
	end)
}
