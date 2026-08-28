ChapelNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual", "Beli Cincin Pertunangan"}

		if player:hasLegend("engaged") and not player:hasLegend("married") and not player:hasLegend("forged_blood_oath") and not player:hasLegend("sealed_blood_oath") and player.registry[
			"partner1"
		] ~= 0 and player.registry["partner2"] ~= 0 then
			table.insert(opts, "Break Off Engagement")
		end

		if player:hasLegend("engaged") and not player:hasLegend("married") and not player:hasLegend("forged_blood_oath") and not player:hasLegend("sealed_blood_oath") and player.registry[
			"partner1"
		] ~= 0 and player.registry["partner2"] ~= 0 then
			table.insert(opts, "Marriage")
		end

		if player:hasLegend("married") and not player:hasLegend("engaged") and not player:hasLegend("forged_blood_oath") and not player:hasLegend("sealed_blood_oath") then
			table.insert(opts, "Divorce")
		end

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				ChapelNpc.buyItems()
			)
		elseif menu == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				ChapelNpc.sellItems()
			)
		elseif menu == "Beli Cincin Pertunangan" then
			if os.time() < player.registry["engagement_timer"] then
				player:dialogSeq(
					{
						t,
						"Wah! Bukankah kau baru saja ke sini? Biarkan hatimu mendingin dulu dari cinta terakhirmu."
					},
					0
				)
				return
			end

			if player:hasLegend("married") or player:hasLegend("engaged") or player:hasLegend("forged_blood_oath") or player:hasLegend("sealed_blood_oath") then
				player:dialogSeq(
					{
						t,
						"Wah! Hatimu sudah terikat pada orang lain."
					},
					0
				)
				return
			end

			local choice = player:menuSeq(
				"Sudahkah kau bertemu seseorang yang kelak kau harap kau nikahi?",
				{
					"Ya, aku sangat jatuh cinta!",
					"Maksudmu aku harus MENCINTAI dia?"
				},
				{}
			)

			if choice == 1 then
				local choice2 = player:menuSeq(
					"Cincin pertunangannya berharga " .. Item("engagement_ring").price .. " emas. Kau mau membelinya?",
					{
						"Tidak ada harga yang terlalu mahal untuk cintaku.",
						"Semahal itu?!? Lupakan saja!"
					},
					{}
				)

				if choice2 == 1 then
					local buyPrice = Item("engagement_ring").price

					if player.money < buyPrice then
						player:dialogSeq(
							{
								t,
								"Kembalilah kalau kau sanggup membayar janji itu."
							},
							1
						)
						return
					end

					player:removeGold(buyPrice)
					player:addItem("engagement_ring", 1)
					if not player:hasSpell("propose") then
						player:addSpell("propose")
					end
					player.registry["engagement_timer"] = os.time() + 86400

					-- current time + 24 hrs
					player:dialogSeq(
						{
							t,
							"Untuk melamar, rapal mantra ini di dekat kekasihmu, lalu ikuti petunjuknya. Pastikan cincinmu kau bawa!"
						},
						0
					)
					return
				elseif choice2 == 2 then
					player:dialogSeq(
						{t, "Kembalilah kalau hatimu sudah siap."},
						0
					)
					return
				end
			elseif choice == 2 then
				player:dialogSeq({t, "Kembalilah kalau hatimu sudah siap."}, 0)
				return
			end
		elseif menu == "Break Off Engagement" then
			player:dialogSeq(
				{
					t,
					"Sedih sekali ini harus terjadi. Setidaknya kau sampai pada keputusan ini sebelum menikah."
				},
				1
			)

			local var = player:menuSeq(
				"Kau yakin ingin membatalkan pertunangan ini?",
				{
					"Ya, itu perlu (Kau akan kehilangan sebagian XP)",
					"Tidak, aku perlu berpikir lagi."
				},
				{}
			)

			if var == 1 then
				local penalty = player.baseMagic * 1000

				if penalty > 4294967295 then
					penalty = 4294967295
				end

				if player.exp < penalty then
					player.exp = 0
				else
					player.exp = player.exp - penalty
				end

				player:removeLegendbyName("engaged")
				player.registry["partner1"] = 0
				player.registry["partner2"] = 0
				player:sendStatus()
				player:dialogSeq({t, "Sudah selesai."}, 0)
				return
			elseif var == 2 then
				player:dialogSeq(
					{t, "Kuharap hubungan kalian masih bisa diselamatkan."},
					0
				)
				return
			end
		elseif menu == "Marriage" then
			if (os.time() < player.registry["marriage_timer"] and not Config.shotgunWeddingEnabled) then
				player:dialogSeq(
					{
						t,
						"Kau baru saja bertunangan. Kembalilah dalam " .. playerTimerValues(
							player,
							"marriage_timer"
						)
					},
					0
				)
				return
			end

			local proposer = Player(player.registry["partner1"])
			local proposee = Player(player.registry["partner2"])

			if proposer == nil or proposee == nil then
				player:dialogSeq(
					{
						t,
						"Kedua pihak harus hadir agar upacaranya bisa dimulai"
					},
					0
				)
				return
			end

			if proposer:hasLegend("married") then
				player:dialogSeq({t, "Orang itu sudah menikah."}, 0)
				return
			end

			if proposer:hasLegend("sealed_blood_oath") or proposer:hasLegend("forged_blood_oath") then
				player:dialogSeq(
					{t, "Orang itu sudah terikat dalam ikatan darah."},
					0
				)
				return
			end

			if player.registry["partner2"] ~= player.ID then
				-- this logic returns true if player accessing menu is the person who orignally proposed the marriage
				player:dialogSeq(
					{t, "Yang dilamar yang harus memulai upacara pernikahan."},
					0
				)
				return
			end

			local choice = player:menuSeq(
				"Kau yakin ingin mengabdikan diri kepada lelaki atau perempuan ini seumur hidup?",
				{"Ya", "Tidak"},
				{}
			)

			if choice == 1 then
				-- Yes
				ChapelNpc.marriageprompt(proposer, proposee)
			elseif choice == 2 then
				-- No
				player:dialogSeq(
					{t, "Kembalilah kalau tekadmu menikah sudah bulat."},
					0
				)
			end
		elseif menu == "Divorce" then
			--if player.gmLevel == 0 then player:dialogSeq({t,"Disabled until further notice."},0) return end

			player:dialogSeq(
				{
					t,
					"Aduh! Kau membuat kesalahan besar!",
					"Namun aku bisa membantumu memperoleh perceraian yang kau inginkan."
				},
				1
			)

			local expCost = player.baseHealth * 2550
			local choice = player:menuString(
				"Biayanya " .. Tools.formatNumber(expCost) .. " pengalaman. Kau yakin ingin bercerai?",
				{"Ya", "Tidak"}
			)

			if choice == "Ya" then
				if player.exp < expCost then
					player:dialogSeq(
						{
							t,
							"Hmmm.. pengalamanmu tidak cukup untuk bercerai, tetapi ada hal lain yang bisa kau tawarkan."
						},
						1
					)

					local vitaPenalty = 8000
					local manaPenalty = 4000
					local stat = ""

					local choice2 = player:menuSeq(
						"Mungkin penderitaan jasmani sudah cukup?",
						{
							"Sacrifice " .. vitaPenalty .. " Vita",
							"Sacrifice " .. manaPenalty .. " Mana",
							"Aku lebih baik tidak."
						},
						{}
					)
					local penalty = 0

					if choice2 == 1 then
						stat = "Vita"
						penalty = vitaPenalty
					elseif choice2 == 2 then
						stat = "Mana"
						penalty = manaPenalty
					end

					local confirm = player:menuSeq(
						"Harganya " .. Tools.formatNumber(penalty) .. " base " .. stat .. " sebagai hukuman. Lanjutkan?",
						{"Ya, lakukan", "Tidak, lupakan saja"},
						{}
					)

					if confirm == 1 then
						if choice2 == 1 and player.baseHealth < vitaPenalty then
							player:dialogSeq(
								{
									t,
									"Kau perlu lebih banyak pengalaman pada kesehatanmu sebelum bisa berkorban seperti ini."
								},
								0
							)
							return
						end

						if choice2 == 2 and player.baseMagic < manaPenalty then
							player:dialogSeq(
								{
									t,
									"Kau perlu lebih banyak pengalaman pada sihirmu sebelum bisa berkorban seperti ini."
								},
								0
							)
							return
						end

						player.partner = 0
						player:removeLegendbyName("married")
						player:removeItem("love", 1)

						if choice2 == 1 then
							player.baseHealth = player.baseHealth - penalty
						elseif choice2 == 2 then
							player.baseMagic = player.baseMagic - penalty
						end

						player.registry["baseHealth"] = player.baseHealth
						player.registry["baseMagic"] = player.baseMagic

						player:calcStat()

						player:dialogSeq({t, "Kau kini bercerai."}, 0)
						return
					end

					return
				elseif player.exp >= expCost then
					local confirmXPLoss = player:menuSeq(
						"Harganya " .. Tools.formatNumber(expCost) .. " pengalaman sebagai hukuman. Lanjutkan?",
						{"Ya, lakukan", "Tidak, lupakan saja"},
						{}
					)

					if confirmXPLoss == 1 then
						player.exp = player.exp - expCost
						player:sendStatus()
						player.partner = 0
						player:removeLegendbyName("married")
						player:removeItem("love", 1)
						player:dialogSeq({t, "Kau kini bercerai."}, 0)
						return
					end
				end
			elseif choice == "Tidak" then
				player:dialogSeq(
					{
						t,
						"Kesabaran dan kasih akan menyelamatkan pernikahanmu.\n\nPerceraian bukan perkara sepele."
					},
					0
				)
				return
			end
		end
	end),

	marriageprompt = async(function(proposer, proposee)
		local choice = proposer:menuSeq(
			"Bersediakah kau, " .. proposer.name .. " ambil " .. proposee.name .. " sebagai pasanganmu?",
			{"Aku bersedia. (kau akan kehilangan banyak xp bila bercerai)", "Aku tidak bersedia."},
			{}
		)

		if choice == 1 then
			proposer:sendMinitext("Dengan ini kunyatakan kalian (menikah)")
			proposee:sendMinitext("Dengan ini kunyatakan kalian (menikah)")

			proposer:removeLegendbyName("engaged")
			proposer:addLegend(
				"Menikah dengan $player (" .. curT() .. ")",
				"married",
				6,
				1,
				proposee.ID
			)

			proposee:removeLegendbyName("engaged")
			proposee:addLegend(
				"Menikah dengan $player (" .. curT() .. ")",
				"married",
				6,
				1,
				proposer.ID
			)

			proposer.registry["partner1"] = 0
			proposer.registry["partner2"] = 0
			proposee.registry["partner1"] = 0
			proposee.registry["partner2"] = 0

			proposer.registry["marriage_timer"] = 0
			proposee.registry["marriage_timer"] = 0

			proposer.partner = proposee.ID
			proposee.partner = proposer.ID

			proposer:addItem("love", 1)
			proposee:addItem("love", 1)

			proposer:dialog("Selamat, kalian berdua kini menikah.", {})
			proposee:dialog("Selamat, kalian berdua kini menikah.", {})
		elseif choice == 2 then
			proposer:sendMinitext("Sepertinya pasanganmu belum yakin pada pernikahan ini.")
			proposer:freeAsync()
			proposer:dialog("Apa lagi selain cinta yang bisa bertahan selamanya?", {})
		end
	end),

	buyItems = function()
		local buyItems = {
			"love",
			"cooked_fish",
			"rose_petals"
		}

		return buyItems
	end,

	sellItems = function()
		if (Config.bossDropSalesEnabled) then
			local sellItems = {
				"fragile_rose",
				"purified_water"
			}

			return sellItems
		else
			return {}
		end
	end
}
