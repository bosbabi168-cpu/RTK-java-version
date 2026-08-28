MagistrateNpc = {
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

		-- 1 - chongun
		-- 2 - barbarian
		-- 3 - do
		-- subpaths released = 0 means subpaths are out, 1 means we're working on it
		if player.class == 1 and (player.quest["subpath_trials"] == 0 or player.quest["subpath_trials"] == 14) and (player.gameRegistry["subpaths_released"] == 0 or player.gmLevel == 99) then
			table.insert(opts, "Bergabung dengan Chongunate")
		end

		if player.quest["subpath_trials"] == 14 then
			table.insert(opts, "Abandon Trials")
		end

		local buysellopts = {
			"rabbit_meat",
			"meat_scrap",
			"horse_meat",
			"antler",
			"bears_liver",
			"tigers_heart"
		}

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				buysellopts
			)
		elseif menu == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				buysellopts
			)
		elseif menu == "Bergabung dengan Chongunate" then
			if player.level < 50 then
				player:dialogSeq(
					{t, "Kau masih terlalu muda untuk bergabung sekarang."},
					0
				)
			end

			if not player:karmaCheck("dog") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player.quest["subpath_trials"] == 0 then
				local join = player:menuString(
					"Apakah kau ingin bergabung dengan Chongunate?",
					{"Ya", "Tidak"}
				)
				if join == "Ya" then
					player.quest["subpath_trials"] = 14
					player:dialogSeq(
						{
							t,
							"Tuntaskan ujian-ujianku untuk memahami jalan Chongun."
						},
						0
					)
				else
					player:dialogSeq({t, "Jangan buang waktuku."}, 0)
				end
			elseif player.quest["subpath_trials"] == 14 then
				local trialstable = {}
				if player:hasLegend("chongun_trial_of_honor") == false then
					table.insert(trialstable, "Ujian Kehormatan")
				end
				if player:hasLegend("chongun_trial_of_valor") == false then
					table.insert(trialstable, "Ujian Keberanian")
				end
				if player:hasLegend("chongun_trial_of_wisdom") == false then
					table.insert(trialstable, "Ujian Kebijaksanaan")
				end
				if player:hasLegend("chongun_trial_of_patience") == false then
					table.insert(trialstable, "Ujian Kesabaran")
				end

				local trials = player:menuString(
					"Apakah kau ingin bergabung dengan Chongunate?",
					trialstable
				)
				if trials == "Ujian Kesabaran" then
					MagistrateNpc.chongun_trial_of_patience(player)
				end
				if trials == "Ujian Kehormatan" then
					MagistrateNpc.chongun_trial_of_honor(player)
				end
				if trials == "Ujian Kebijaksanaan" then
					MagistrateNpc.chongun_trial_of_wisdom(player)
				end
				if trials == "Ujian Keberanian" then
					MagistrateNpc.chongun_trial_of_valor(player)
				end
			else
				player:dialogSeq(
					{
						t,
						"Kau harus meninggalkan ujianmu yang lain sebelum memulai yang ini."
					},
					0
				)
			end
		elseif menu == "Abandon Trials" then
			local abandon = player:menuString(
				"Kau yakin ingin meninggalkan ujianmu?",
				{"Ya", "Tidak"}
			)
			if abandon == "Ya" then
				player.quest["subpath_trials"] = 0
				player.quest["patience_start"] = 0
				player.quest["chongun_valor"] = 0
				player.quest["chongun_valor_rabbit1"] = 0
				player.quest["chongun_valor_rabbit2"] = 0
				player.quest["chongun_valor_rabbit3"] = 0
				player.quest["chongun_valor_monkey1"] = 0
				player.quest["chongun_valor_monkey2"] = 0
				player.quest["chongun_valor_monkey3"] = 0
				player.quest["chongun_valor_dog1"] = 0
				player.quest["chongun_valor_dog2"] = 0
				player.quest["chongun_valor_dog3"] = 0
				player:removeLegendbyName("chongun_trial_of_patience")
				player:removeLegendbyName("chongun_trial_of_honor")
				player:removeLegendbyName("chongun_trial_of_valor")
				player:removeLegendbyName("chongun_trial_of_wisdom")
				player:dialogSeq(
					{t, "Semua yang pernah kau pelajari kini terlupakan."},
					0
				)
			else
				return
			end
		end
	end),
	chongun_trial_of_patience = function(player)
		if player.quest["patience_start"] == 0 then
			player.quest["patience_start"] = os.time()
			player:dialogSeq(
				{
					t,
					"Untuk lulus ujian kesabaran, kau harus menunggu 3 hari. Kembalilah kepadaku setelah selesai."
				},
				0
			)
		else
			if os.time() > player.quest["patience_start"] + 259200 then
				if player:hasLegend("chongun_trial_of_patience") == false then
					player:addLegend(
						"Lulus ujian Chongun: Kesabaran",
						"chongun_trial_of_patience",
						17,
						15
					)
				end
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
			else
				player:dialogSeq({t, "Kau belum lulus ujian ini."}, 0)
			end
		end
	end,
	chongun_trial_of_honor = function(player)
		local diag = {
			t,
			"Sebagai Chongun, kami menghormati dan mendukung kerajaan kami beserta rakyatnya. Carilah dan tuntaskan 10 tugas kecil."
		}

		if (player.quest["minorquestcomplete"] >= 10) then
			if player:hasLegend("chongun_trial_of_honor") == false then
				player:addLegend(
					"Lulus ujian Chongun: Kehormatan",
					"chongun_trial_of_honor",
					17,
					15
				)
			end
			table.insert(diag, "Kau sudah melakukannya dengan baik.")
		else
			table.insert(
				diag,
				"Silakan kembali kalau tugas ini sudah kau selesaikan."
			)
		end

		player:dialogSeq(diag, 0)
	end,
	chongun_trial_of_valor = function(player)
		-- kill rabbit, monkey and dog bosses
		local diag = {
			t,
			"Untuk membuktikan kekuatan dan keberanianmu, bunuhlah penjaga keberuntungan, penjaga mawar, dan penjaga perlindungan pertempuran."
		}

		if player.quest["chongun_valor"] == 0 then
			player.quest["chongun_valor_rabbit1"] = player:killCount("hare_witch")
			player.quest["chongun_valor_rabbit2"] = player:killCount("rabbit_witch")
			player.quest["chongun_valor_rabbit3"] = player:killCount("rabbit_avenger")

			player.quest["chongun_valor_monkey1"] = player:killCount("monkey_mauler")
			player.quest["chongun_valor_monkey2"] = player:killCount("monkey_basher")
			player.quest["chongun_valor_monkey3"] = player:killCount("monkey_avenger")

			player.quest["chongun_valor_dog1"] = player:killCount("dog_assassin")
			player.quest["chongun_valor_dog2"] = player:killCount("dog_cutthroat")
			player.quest["chongun_valor_dog3"] = player:killCount("dog_avenger")

			player.quest["chongun_valor"] = 1
		end

		if player.quest["chongun_valor"] == 1 then
			local rabbit = false
			local monkey = false
			local dog = false

			if player.quest["chongun_valor_rabbit1"] > player:killCount("hare_witch") or player.quest[
				"chongun_valor_rabbit2"
			] > player:killCount("rabbit_witch") or player.quest[
				"chongun_valor_rabbit3"
			] > player:killCount("rabbit_avenger") then
				rabbit = true
			end

			if player.quest["chongun_valor_monkey1"] > player:killCount("monkey_mauler") or player.quest[
				"chongun_valor_monkey2"
			] > player:killCount("monkey_basher") or player.quest[
				"chongun_valor_monkey3"
			] > player:killCount("monkey_avenger") then
				monkey = true
			end

			if player.quest["chongun_valor_dog1"] > player:killCount("dog_assassin") or player.quest[
				"chongun_valor_dog2"
			] > player:killCount("dog_cutthroat") or player.quest[
				"chongun_valor_dog3"
			] > player:killCount("dog_avenger") then
				dog = true
			end

			if rabbit and monkey and dog then
				player.quest["chongun_valor_rabbit1"] = 0
				player.quest["chongun_valor_rabbit2"] = 0
				player.quest["chongun_valor_rabbit3"] = 0

				player.quest["chongun_valor_monkey1"] = 0
				player.quest["chongun_valor_monkey2"] = 0
				player.quest["chongun_valor_monkey3"] = 0

				player.quest["chongun_valor_dog1"] = 0
				player.quest["chongun_valor_dog2"] = 0
				player.quest["chongun_valor_dog3"] = 0
				if player:hasLegend("chongun_trial_of_valor") == false then
					player:addLegend(
						"Lulus ujian Chongun: Keberanian",
						"chongun_trial_of_valor",
						17,
						15
					)
				end
				table.insert(diag, "Kau sudah melakukannya dengan baik.")
			else
				table.insert(
					diag,
					"Silakan kembali kalau tugas ini sudah kau selesaikan."
				)
			end
		end

		player:dialogSeq(diag, 0)
	end,
	chongun_trial_of_wisdom = function(player)
		-- complete questionaire
	end,

	action = function(npc)
	end,

	buyItems = function()
		local buyItems = {
			"rabbit_meat",
			"meat_scrap",
			"horse_meat",
			"antler",
			"bears_liver",
			"tigers_heart"
		}
		return buyItems
	end,

	sellItems = function()
		return self:buyItems()
	end
}
