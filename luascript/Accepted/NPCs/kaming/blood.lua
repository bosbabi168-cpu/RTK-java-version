BloodNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local spells = {"slash_warrior"}

		local opts = {"Beli", "Jual", "Forget Secret"}

		if player:hasLegend("forged_blood_oath") and not player:hasLegend("engaged") and not player:hasLegend("married") then
			table.insert(opts, "Break Off Blood Oath")
		end

		if not player:hasLegend("forged_blood_oath") and not player:hasLegend("sealed_blood_oath") and not player:hasLegend("married") and not player:hasLegend("engaged") and player.partner == 0 then
			table.insert(opts, "Ikat sumpah darah")
		end

		if player:hasLegend("forged_blood_oath") and not player:hasLegend("married") and not player:hasLegend("engaged") and player.partner == 0 then
			table.insert(opts, "Seal blood oath")
		end

		if not player:hasLegend("engaged") and not player:hasLegend("married") and not player:hasLegend("forged_blood_oath") and player:hasLegend("sealed_blood_oath") then
			table.insert(opts, "Unseal Blood Oath")
		end

		if player.baseClass == 1 then
			table.insert(opts, "Pelajari Mantra")
		end

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)
		local choice2 = ""

		if choice == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				BloodNpc.buyItems()
			)
		elseif choice == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				BloodNpc.sellItems()
			)
		elseif choice == "Forget Secret" then
			player:forgetSpell(npc)
		elseif choice == "Pelajari Mantra" then
			player:learnSpecificSpells(npc, spells)
		elseif choice == "Ikat sumpah darah" then
			player:dialogSeq(
				{
					t,
					"Jadi kau ingin mengikat janji dengan orang lain lewat percampuran darah? Kau datang ke tempat yang tepat."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Ritual ini berumur berabad-abad dan bukan untuk yang berhati lemah; ongkos dan risikonya besar. Tapi jangan sampai itu menghalangimu menunjukkan kepedulian pada seseorang."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Kalau kau rela melepas sedikit emas, aku bisa membantumu."
				},
				1
			)
			choice2 = player:menuString(
				"Bersediakah kau memberikan 1.000 emas demi pengetahuan tentang ritual ini?",
				{"Ya, aku mau ini.", "Tidak, aku berubah pikiran."},
				{}
			)

			if choice2 == "Ya, aku mau ini." then
				if player.money < 1000 then
					player:dialogSeq(
						{
							t,
							"Pengetahuan tentang ritual ini ada harganya, dan harus dibayar sebelum aku bersedia membagikannya. Kalau kau masih ingin belajar, kembalilah kepadaku kalau emasmu sudah cukup."
						},
						0
					)
					return
				end

				player:removeGold(1000)
				if not player:hasSpell("blood_oath") then
					player:addSpell("blood_oath")
				end

				player:dialogSeq(
					{
						t,
						"Aku baru saja memberimu pengetahuan yang kau butuhkan untuk memulai ritual, dan kudoakan kau beruntung. Ritual ini tidak mudah."
					},
					0
				)
				return
			elseif choice2 == "Tidak, aku berubah pikiran." then
				player:dialogSeq(
					{
						t,
						"Untungnya janji semacam ini tidak untuk semua orang, dan lebih baik kau menyadarinya sekarang daripada nanti setelah darah kalian tercampur."
					},
					0
				)
				return
			end
		elseif choice == "Seal blood oath" then
			if (os.time() < player.registry["seal_blood_oath_timer"] and not Config.shotgunWeddingEnabled) then
				player:dialogSeq(
					{
						t,
						"Kau baru saja mengikat sumpah darahmu. Kembalilah dalam " .. playerTimerValues(
							player,
							"seal_blood_oath_timer"
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

			if proposer:hasLegend("married") or player:hasLegend("engaged") then
				player:dialogSeq(
					{t, "Orang itu sudah bertunangan atau menikah."},
					0
				)
				return
			end

			if proposer:hasLegend("sealed_blood_oath") then
				player:dialogSeq(
					{t, "Orang itu sudah menjadi bagian dari ikatan darah."},
					0
				)
				return
			end

			if player.registry["partner2"] ~= player.ID then
				-- this logic returns true if player accessing menu is the person who orignally proposed the marriage
				player:dialogSeq(
					{t, "Yang dilamar yang harus memulai upacara sumpah darah."},
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
				BloodNpc.sealbloodoath(proposer, proposee)
			elseif choice == 2 then
				-- No
				player:dialogSeq(
					{
						t,
						"Kembalilah kalau tekadmu menyatukan darah sudah bulat."
					},
					0
				)
			end
		elseif choice == "Break Off Blood Oath" then
			player:dialogSeq(
				{
					t,
					"Sedih sekali ini harus terjadi. Setidaknya kau sampai pada keputusan ini sebelum sumpahmu dimeteraikan."
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

				player:removeLegendbyName("forged_blood_oath")
				player.registry["partner1"] = 0
				player.registry["partner2"] = 0
				player:sendStatus()
				player:dialogSeq({t, "Sudah selesai."}, 0)
				return
			elseif var == 2 then
				player:dialogSeq({t, "Kuharap ikatan kalian masih bisa diselamatkan."}, 0)
				return
			end
		elseif choice == "Unseal Blood Oath" then
			player:dialogSeq(
				{
					t,
					"Aduh! Kau membuat kesalahan besar!",
					"Namun aku bisa membantumu memutus ikatan darahmu."
				},
				1
			)

			local expCost = player.baseHealth * 2550
			local confirm = player:menuString(
				"Biayanya " .. Tools.formatNumber(expCost) .. " pengalaman. Kau yakin ingin memutus ikatan darahmu?",
				{"Ya", "Tidak"}
			)

			if confirm == "Ya" then
				if player.exp < expCost then
					player:dialogSeq(
						{
							t,
							"Hmmm.. pengalamanmu tidak cukup untuk memutus ikatan darahmu, tetapi ada hal lain yang bisa kau tawarkan."
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

					local confirm2 = player:menuSeq(
						"Harganya " .. Tools.formatNumber(penalty) .. " base " .. stat .. " sebagai hukuman. Lanjutkan?",
						{"Ya, lakukan", "Tidak, lupakan saja"},
						{}
					)

					if confirm2 == 1 then
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
						player:removeLegendbyName("sealed_blood_oath")
						player:removeItem("blood_stone", 1)

						if choice2 == 1 then
							player.baseHealth = player.baseHealth - penalty
						elseif choice2 == 2 then
							player.baseMagic = player.baseMagic - penalty
						end

						player.registry["baseHealth"] = player.baseHealth
						player.registry["baseMagic"] = player.baseMagic

						player:calcStat()

						player:dialogSeq(
							{t, "Kau kini terbebas dari ikatan darah."},
							0
						)
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
						player:removeLegendbyName("sealed_blood_oath")
						player:removeItem("blood_stone", 1)
						player:dialogSeq(
							{t, "Kau kini terbebas dari ikatan darah."},
							0
						)
						return
					end
				end
			elseif confirm == "Tidak" then
				player:dialogSeq(
					{
						t,
						"Kesabaran dan kasih akan menyelamatkan ikatan darahmu.\n\nMemutus ikatan darah bukan perkara sepele."
					},
					0
				)
				return
			end
		end
	end),

	sealbloodoath = async(function(proposer, proposee)
		local choice = proposer:menuSeq(
			"Bersediakah kau, " .. proposer.name .. " ingin menyatukan darahmu dengan " .. proposee.name .. "?",
			{
				"Aku bersedia. (kau akan kehilangan banyak xp kalau memutus ikatan darah ini)",
				"Aku tidak bersedia."
			},
			{}
		)

		if choice == 1 then
			proposer:sendMinitext("Kau kini terikat darah dengan " .. proposee.name)
			proposee:sendMinitext("Kau kini terikat darah dengan " .. proposer.name)

			proposer:removeLegendbyName("forged_blood_oath")
			proposee:removeLegendbyName("forged_blood_oath")

			if proposer.sex == 0 then
				-- male
				proposer:addLegend(
					"$player's Blood brother (" .. curT() .. ")",
					"sealed_blood_oath",
					51,
					1,
					proposee.ID
				)
			elseif proposer.sex == 1 then
				-- female
				proposer:addLegend(
					"$player's Blood sister (" .. curT() .. ")",
					"sealed_blood_oath",
					51,
					1,
					proposee.ID
				)
			end

			if proposee.sex == 0 then
				-- male
				proposee:addLegend(
					"$player's Blood brother (" .. curT() .. ")",
					"sealed_blood_oath",
					51,
					1,
					proposer.ID
				)
			elseif proposee.sex == 1 then
				-- female
				proposee:addLegend(
					"$player's Blood sister (" .. curT() .. ")",
					"sealed_blood_oath",
					51,
					1,
					proposer.ID
				)
			end

			proposer.registry["partner1"] = 0
			proposer.registry["partner2"] = 0
			proposee.registry["partner1"] = 0
			proposee.registry["partner2"] = 0

			proposer.partner = proposee.ID
			proposee.partner = proposer.ID

			proposer:addItem("blood_stone", 1)
			proposee:addItem("blood_stone", 1)

			proposer:dialog(
				"Selamat, kalian berdua kini bersaudara darah.",
				{}
			)
			proposee:dialog(
				"Selamat, kalian berdua kini bersaudara darah.",
				{}
			)

			proposee:sendStatus()
			proposer:sendStatus()
		elseif choice == 2 then
			proposer:sendMinitext("Sepertinya pasanganmu belum yakin pada ikatan darah ini.")
		end
	end),

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

		local item = Item("frost_sabre")
		local tfrostsabre = {graphic = item.icon, color = item.iconC}

		if speech == "binatang es" then
			Tools.checkKarma(player)

			if player.level < 7 then
				return
			end

			if player:hasLegend("defeated_ice_beast") == true then
				player:dialogSeq(
					{t, "\"Semoga Frost sabre-mu berguna!\""},
					1
				)
				return
			end

			if player.registry["paid_gold_for_frost_sabre"] == 1 then
				if player:hasItem("ice_heart", 1) == true then
					player:dialogSeq(
						{
							t,
							"Sonhi itu tampak terkejut. \"Aku tidak tahu bagaimana orang selemah kau bisa mengalahkan Ice beast, tetapi entah bagaimana kau menang.\"",
							"\"Seperti yang kujanjikan, akan kutempakan Frost sabre untukmu.\""
						},
						1
					)
					player:removeItem("ice_heart", 1, 9)
					player:addItem("frost_sabre", 1, 0, player.ID)
					player:giveXP(2300)
					player:addLegend(
						"Mengalahkan Ice beast (" .. curT() .. ")",
						"defeated_ice_beast",
						5,
						128
					)
					player.registry["paid_gold_for_frost_sabre"] = 0
					return
				end

				player:dialogSeq(
					{
						t,
						"Sonhi itu tampak menahan tawa. \"Bawakan aku Ice heart dan akan kubuatkan Frost sabre untukmu.\""
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Sonhi itu menyeringai. \"Ice Beast, ya? Kau pasti mengincar Frost sabre.\"",
					"\"Kau pernah dengar tentang Frost sabre, kan? Belum? Kalau begitu biar kuceritakan apa yang kau lewatkan.\""
				},
				1
			)

			player:dialogSeq(
				{
					tfrostsabre,
					"\"Meski hanya senjata sederhana, Frost sabre punya banyak kekuatan hebat.\"",
					"\"Saat kau mati, ia tidak meninggalkanmu. Saat aus, ia mudah diperbaiki. Dalam pertempuran, kadang ia membekukan musuhmu sehingga lebih mudah dikenai.\"",
					"\"Yang mungkin paling mengesankan: hanya KAU yang bisa memakai Frost sabre-mu kalau ia ditempa untukmu.\nSayangnya sangat, sangat sedikit yang tahu cara membuatnya.\""
				},
				1
			)

			player:dialogSeq(
				{
					t,
					"\"Aku tahu apa yang kau pikirkan. Ya, aku tahu cara membuat Frost sabre. Dari matamu kelihatan kau sangat menginginkannya, bukan?\""
				},
				1
			)

			local choice = player:menuSeq(
				"\"Hanya dengan 100 emas akan kutempakan satu untukmu, asal kau membawakan bahan yang diperlukan. Kau mau membayar?\"",
				{"Ya, aku mau Frost sabre.", "Tidak, uangku kusimpan saja."},
				{}
			)

			if choice == 1 then
				if player.money < 100 then
					player:dialogSeq(
						{t, "Kembalilah kalau emasmu lebih banyak."},
						1
					)
					return
				else
					player:removeGold(100)
					player.registry["paid_gold_for_frost_sabre"] = 1
				end

				player:dialogSeq(
					{
						t,
						"\"Untuk menempanya aku butuh Ice heart dari Ice Beast yang perkasa dan jahat.\"",
						"\"Di mana mencari Ice Beast? Aku tidak tahu. Kami orang Sonhi bukan dari daerah ini. Mungkin yang sudah lama tinggal di sini tahu.\"",
						"Saat kau pergi, kau mendengar kapten Sonhi terkekeh sendiri, \"Sekalipun si tolol itu menemukan Ice beast, ia pasti mati. Seratus emas yang mudah, heh, heh.\""
					},
					0
				)
			elseif choice == 2 then
				player:dialogSeq(
					{
						t,
						"\"Terserah kau... kalau begitu kurasa Frost sabre-nya akan kubuat untuk orang lain.\""
					},
					1
				)
				return
			end
		end

		if speech == "segel" then
			Tools.checkKarma(player)

			if player.quest["wind_armor"] > 0 then
				if player.quest["gave_weaving_tools_sya"] == 0 then
					player:dialogSeq(
						{
							t,
							"Ehh... aku sungguh tidak paham apa yang kau bicarakan."
						},
						0
					)
					return
				end

				if player.quest["frost_sabre_for_seal"] == 0 then
					player.quest["frost_sabre_for_seal"] = 1
					player:dialogSeq(
						{
							t,
							"Eh? Siapa yang memberitahumu tentang segel itu? Sudahlah, kurasa aku tahu; pasti si mulut besar Sya.",
							"Gah! Kurasa itu bukan salahnya, aku yang harus belajar tutup mulut.",
							"Rahasia itu bahkan bukan milikku untuk dibagikan; segelnya ada di tangan orang lain.",
							"Seharusnya aku tidak menceritakan ini padamu, tetapi mulutku ini selalu menyeretku ke masalah baru.",
							"Mungkin itu sebabnya KaMing meninggalkanku membusuk di sini bersama orang kota jelek sepertimu...",
							"Ya, yah, pokoknya... ia meninggalkanku... di sini.",
							"Begini saja - aku mengajarkan rahasia Frost sabre kepada banyak orang, tetapi sudah lama aku bermimpi memiliki satu untuk diriku sendiri.",
							"Kalau kau membawakan satu untukku, akan kuberitahu siapa pemegangnya dan bagaimana cara mendapatkannya!"
						},
						0
					)
				end

				if player.quest["frost_sabre_for_seal"] == 1 then
					if player:hasItem("frost_sabre", 1) ~= true then
						player:dialogSeq(
							{
								t,
								"Aku masih menunggu frost sabre itu sebelum bisa bercerita lebih jauh tentang segelnya."
							},
							0
						)
						return
					end

					player.quest["frost_sabre_for_seal"] = 2
					player:removeItem("frost_sabre", 1, 9)

					player:dialogSeq(
						{
							t,
							"Ini sabre yang bagus sekali! Terima kasih banyak sudah membawakannya.",
							"Aku tidak peduli siapa pemiliknya, aku tidak akan pernah memakainya, tetapi memiliki benda ini dalam koleksiku adalah kehormatan",
							"Nah, seperti yang dijanjikan, inilah keterangan yang kau cari. Gan dulu pandai besi pribadi KaMing sendiri.",
							"Suatu kali ketika KaMing menitipkan zirahnya untuk diperbaiki seusai pertempuran, Gan menemukan segel itu di salah satu lipatannya.",
							"Ia menyimpannya untuk dikembalikan kepada KaMing, tetapi KaMing tidak pernah datang mengambil zirah itu.",
							"Sebaiknya kau bicarakan itu dengannya, tetapi jangan sebut segelnya; ia tidak akan langsung menyerahkannya.",
							"Kau harus mencari cara lain supaya ia mau membicarakannya."
						},
						0
					)
				end

				if player.quest["frost_sabre_for_seal"] == 2 then
					player:dialogSeq(
						{
							t,
							"Sekali lagi terima kasih frost sabre-nya. Jangan lupa menemui Gan."
						},
						0
					)
				end
			end
		end
	end),

	buyItems = function()
		local buyItems = {
			"blood_stone",
			"cooked_fish",
			"rose_petals"
		}

		return buyItems
	end,

	sellItems = function()
		return ChapelNpc.sellItems()
	end
}
