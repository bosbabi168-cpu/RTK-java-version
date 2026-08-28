MinNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local options = {"Beli", "Jual", "Keahlian Memasak"}

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			options
		)

		if choice == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				MinNpc.buyItems(npc)
			)
		elseif choice == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				MinNpc.sellItems(npc)
			)
		elseif choice == "Keahlian Memasak" then
			MinNpc.cookingSkills(player, npc)
		end
	end),

	buyItems = function(npc)
		local buyItems = {
			"acorn",
			"chestnut",
			"meat_scrap",
			"rabbit_meat",
			"wolf_meat",
			"tigers_heart"
		}

		return buyItems
	end,

	sellItems = function(npc)
		local sellItems = ButcherNpc.sellItems()

		table.insert(sellItems, "egg")
		table.insert(sellItems, "tiger_meat")
		table.insert(sellItems, "tigers_heart")
		table.insert(sellItems, "splendid_tiger_pelt")

		if (Config.bossDropSalesEnabled) then
			table.insert(sellItems, "ambrosia")
			table.insert(sellItems, "dragons_liver")
		end

		return sellItems
	end,

	cookingSkills = function(player, npc)
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
				"Ah, nikmatnya memasak. Itu kegemaran utama orang Hausson.",
				"Kami hanya tahu cara mengolah bahan dan membuat hidangan mi sederhana.",
				"Tapi aku yakin ada resep-resep hebat dari kerajaan lain yang bisa dipakai.",
				"Untuk sekarang aku hanya bisa membantumu menyiapkan gandum, telur, dan beberapa jenis daging untuk dimasak.",
				"Setelah kau bisa menyiapkan bahan makanan, akan kubantu kau membuat mi.",
				"Katakan saja apa yang ingin kau siapkan, dan akan kubantu."
			},
			0
		)
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

		if speech:match("prepare ") then
			crafting.craftingDialog(player, npc, speech)
		end

		if speech == "kebajikan" and player.quest["gloth_clicked"] == 1 and player.quest["wind_armor"] == 1 then
			Tools.checkKarma(player)

			if player.quest["min_clicked"] == 0 then
				player.quest["min_clicked"] = 1
				player:dialogSeq(
					{
						t,
						"Jadi kau mencari jalur Virtue? Ya, akulah yang menempatkan penjaga untuk melindungi rahasia itu.",
						"Kau sudah bekerja baik hingga sampai sejauh ini, dan aku senang dengan kemajuanmu.",
						"Kau membuktikan kebajikanmu cukup untuk sampai sejauh ini, dan itu sendiri sudah pencapaian besar.",
						"((Perempuan itu tersenyum kepadamu))",
						"Kau sendiri bahkan tidak menyadarinya, bukan? Pada waktunya kau akan sadar; aku yakin itu.",
						"Bukan hanya aku pemegang rahasia itu; orang lain juga harus menilaimu.",
						"Dia kawan lamaku, Chul, yang tinggal di ibu kota Nagnang.",
						"Bawakan dia Stardrop, dan ia akan bercerita lebih banyak.",
						"Jaga dirimu dalam perjalanan. Kita akan bertemu lagi; jalanmu masih panjang."
					},
					0
				)
				return
			end

			if player.quest["min_clicked"] == 1 then
				player:dialogSeq(
					{t, "Lanjutkan, kawan; perjalanan ini harus kau tuntaskan sendiri."},
					0
				)
				return
			end
		end

		if speech == "humm dee do dum do hee" then
			Tools.checkKarma(player)

			if player.quest["lost_legend_chest_clicked"] == 0 or player.quest["wind_armor"] == 0 then
				player:dialogSeq(
					{t, "Aku sungguh tidak paham apa yang kau bicarakan."},
					0
				)
				return
			end

			player.quest["min_song_asked"] = 1
			player:dialogSeq(
				{
					t,
					"Ah, kau kembali, dan kau sudah mendengar lagunya. Aku senang kau sampai sejauh ini.",
					"Lagu itu, dulu salah satu kesukaanku.",
					"Andai aku masih ingat nadanya, tetapi sudah lama sekali kulupakan.",
					"Aku hanya ingat mendengarnya di atas kapal yang membawaku ke tanah ini."
				},
				0
			)
		end

		if speech == "kawlana" then
			Tools.checkKarma(player)

			if player.quest["wind_armor"] == 0 or player.quest["dae_shore_paper_burned"] == 0 then
				player:dialogSeq(
					{t, "Aku sungguh tidak paham apa yang kau bicarakan."},
					0
				)
				return
			end

			player.quest["min_kawlana"] = 1
			player:dialogSeq(
				{
					t,
					"Kenapa aku tidak heran kau sampai sejauh ini?",
					"Hatimu kuat dan berapi-api. Kau tidak akan goyah dari tujuanmu.",
					"Aku yakin kau ke sini bukan untuk dipuji, meski pujian tidak pernah merugikan.",
					"Kawlana adalah kata dari kampung halamanku. Ia alam bayangan tempat jiwamu berkelana bebas dan angin kekuatan sejati hidup.",
					"Kawlana adalah sumber kekuatanmu. Ia hidup dan bersemayam di dalam diri kita masing-masing.",
					"Dalam perjalananku, aku butuh cara memancing angin ke jaring supaya bisa ditangkap.",
					"Dengan Kawlana yang bersihir dan berkuasa, aku berhasil mendekatkan angin itu, tetapi dengan pengorbanan berat.",
					"Saat itulah pertempuran yang sesungguhnya dimulai, yang kukira takkan kulewati hidup-hidup.",
					"Syukur kepada bintang-bintang di atas, daya hidupku cukup untuk selamat dari pertempuran itu.",
					"Kau harus memperoleh Kawlana-mu sendiri kalau berniat menangkap angin."
				},
				0
			)
		end

		if speech == "tenun angin" then
			Tools.checkKarma(player)

			if player.quest["wind_armor"] == 0 then
				player:dialogSeq(
					{t, "Aku sungguh tidak paham apa yang kau bicarakan."},
					0
				)
				return
			end

			if player:hasItem("captured_wind", 1) ~= true then
				player:dialogSeq(
					{t, "Kau harus punya angin tangkapan untuk melanjutkan."},
					0
				)
				return
			end

			if player.quest["min_weave_wind"] == 0 then
				player.quest["min_weave_wind"] = 1
				player:dialogSeq(
					{
						t,
						"Astaga... aku tidak akan pernah terbiasa melihat itu.",
						"Angin, terperangkap seperti itu.",
						"Tapi ya, akan kubantu kau menenun angin itu menjadi busana. Hanya aku yang bisa menunjukkan caranya.",
						"Tapi kau butuh bantuan penenun dan penjahit yang setidaknya sudah menguasai kerajinannya.",
						"Berempat (atau bertiga kalau kau sendiri punya keahlian itu) kita bisa membuat zirahnya.",
						"Maaf, bentuknya tidak seperti zirah yang biasa kau pakai. Aku hanya tahu gaya dari tanah kelahiranku.",
						"Pergilah, dan kalau orang-orangnya sudah kau kumpulkan, kembalilah dan kita mulai."
					},
					0
				)
				return
			end

			if player.quest["min_weave_wind"] == 1 then
				local choice = player:menuSeq(
					"Jadi, orang-orang yang kau butuhkan untuk menenun busana ini sudah ada? (Kalau mereka bersamamu, mereka tidak perlu bergrup, cukup hadir.)",
					{"Ya, mereka di sini.", "Tidak, mereka belum ada."},
					{}
				)

				if choice == 1 then
					local input = player:inputLetterCheck(player:input("Siapa yang akan menenun zirahmu?"))
					local weaver = Player(input)

					if weaver == nil then
						player:dialogSeq({t, "Orang itu sedang tidak daring."}, 0)
						return
					end

					input = player:inputLetterCheck(player:input("Siapa yang akan menjahit zirahmu?"))
					local tailor = Player(input)

					if tailor == nil then
						player:dialogSeq({t, "Orang itu sedang tidak daring."}, 0)
						return
					end

					if weaver.m ~= player.m then
						player:dialogSeq(
							{t, "Penenunmu harus ada di sini bersamamu."},
							0
						)
						return
					end
					if tailor.m ~= player.m then
						player:dialogSeq(
							{t, "Penjahitmu harus ada di sini bersamamu."},
							0
						)
						return
					end

					if not crafting.checkSkillLevel(weaver, "weaving", "master") then
						player:dialogSeq(
							{
								t,
								"Keahlianmu belum cukup untuk membuat zirah anginmu. Tingkatkan keahlianmu atau ajak orang lain yang punya keahlian itu."
							},
							0
						)
						return
					end
					if not crafting.checkSkillLevel(tailor, "tailoring", "master") then
						player:dialogSeq(
							{
								t,
								"Keahlianmu belum cukup untuk membuat zirah anginmu. Tingkatkan keahlianmu atau ajak orang lain yang punya keahlian itu."
							},
							0
						)
						return
					end

					local item = ""

					if player.baseClass == 1 and player.sex == 0 then
						-- warrior male
						item = "wind_platemail"
					elseif player.baseClass == 1 and player.sex == 1 then
						-- warrior female
						item = "wind_platemail_dress"
					elseif player.baseClass == 2 and player.sex == 0 then
						-- rogue male
						item = "wind_armor"
					elseif player.baseClass == 2 and player.sex == 1 then
						-- rogue female
						item = "wind_armor_dress"
					elseif player.baseClass == 3 and player.sex == 0 then
						-- mage male
						item = "wind_garb"
					elseif player.baseClass == 3 and player.sex == 1 then
						-- mage female
						item = "wind_skirt"
					elseif player.baseClass == 4 and player.sex == 0 then
						-- poet male
						item = "wind_robes"
					elseif player.baseClass == 4 and player.sex == 1 then
						-- poet female
						item = "wind_gown"
					end

					player:removeItem("captured_wind", 1)
					player:addItem(item, 1, 0, player.ID)

					if not player:hasLegend("captured_the_wind") then
						player:addLegend(
							"Menangkap angin (" .. curT() .. ")",
							"captured_the_wind",
							0,
							128
						)
					end

					weaver:addKarma(1)
					tailor:addKarma(1)

					player.quest["wind_armor"] = 0
					player.quest["min_clicked"] = 0
					player.quest["star_swords"] = 0
					player.quest["min_song_asked"] = 0
					player.quest["kawlana_used"] = 0
					player.quest["kawlana_dropped"] = 0
					player.quest["min_kawlana"] = 0
					player.quest["lost_legend_chest_clicked"] = 0
					player.quest["chu_rua_song"] = 0
					player.quest["chu_rua_song_stanza"] = 0
					player.quest["gan_metal"] = 0
					player.quest["presented_sonhi_pass"] = 0

					player:dialogSeq(
						{
							t,
							"Dan ini dia! Zirah yang sekian lama kau cari!",
							"Kenakan baik-baik, dan kenakan dengan bangga."
						},
						0
					)
				elseif choice == 2 then
					player:dialogSeq(
						{t, "Kembalilah kalau kau sudah siap."},
						0
					)
				end
			end
		end
	end)
}
