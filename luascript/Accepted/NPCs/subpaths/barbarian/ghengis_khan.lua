GhengisKhanNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual", "Ghengis Khan's Welcome"}

		if player.class == 1 and (player.quest["subpath_trials"] == 0 or player.quest["subpath_trials"] == 10) and (player.gameRegistry["subpaths_released"] == 0 or player.gmLevel == 99) then
			table.insert(opts, "Bergabung dengan Barbarian")
		end

		if player.quest["subpath_trials"] == 10 then
			table.insert(opts, "Abandon Trials")
		end

		local buyitems = GhengisKhanNpc.buyItems()
		local sellitems = GhengisKhanNpc.sellItems()

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accommodate some of the things you need. What would you like?",
				buyitems
			)
		elseif menu == "Jual" then
			player:sellExtend("What are you willing to sell today?", sellitems)
		elseif menu == "Ghengis Khan's Welcome" then
			player:dialogSeq({t, "Halo dan selamat datang di Barbarian Cave."}, 0)
			return
		elseif menu == "Bergabung dengan Barbarian" then
			GhengisKhanNpc.joinTheBarbarians(player, npc)
		elseif menu == "Abandon Trials" then
			local abandon = player:menuString(
				"Kau yakin ingin meninggalkan ujianmu?",
				{"Ya", "Tidak"}
			)
			if abandon == "Ya" then
				GhengisKhanNpc.clearQuestLegends(player)
				player:dialogSeq(
					{
						t,
						"Semua yang pernah kau pelajari tentang Barbarian kini terlupakan."
					},
					0
				)
			else
				return
			end
		end
	end),

	clearQuestLegends = function(player)
		player.quest["subpath_trials"] = 0
		player.quest["barbarian_trial"] = 0
		player.quest["barbarian_trial_of_willingness"] = 0
		player.quest["barbarian_trial_of_survival"] = 0
		player.quest["barbarian_trial_of_atonement"] = 0
		player.quest["barbarian_trial_of_atonement_meat_collected"] = 0
		player.quest["barbarian_trial_of_repudiation"] = 0
		player.quest["barbarian_trial_of_competency"] = 0
		player.quest["barbarian_trial_of_competency_prior_wins"] = 0

		player:removeLegendbyName("barbarian_trial_of_willingness")
		player:removeLegendbyName("barbarian_trial_of_survival")
		player:removeLegendbyName("barbarian_trial_of_atonement")
		player:removeLegendbyName("barbarian_trial_of_repudiation")
		player:removeLegendbyName("barbarian_trial_of_competency")
	end,

	action = function(npc)
		local random = math.random(1, 15)
		if random == 1 then
			npc:talk(0, npc.name .. ": Selamat datang di Gua para Barbarian")
		end
	end,

	move = function(npc)
		npc.side = math.random(0, 3)
		npc:sendSide()
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
		return GhengisKhanNpc.buyItems()
	end,

	joinTheBarbarians = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		--[[1. Willingness
	2. Survival
	3. Atonement
	4. Repudiation
	5. Competency]]
		--

		if player.level < 50 then
			player:dialogSeq({t, "Kau masih terlalu muda untuk bergabung sekarang."}, 0)
			return
		end

		if not player:karmaCheck("dog") then
			player:dialogSeq(
				{t, "Jiwamu terlalu kotor. Perbaiki karmamu lalu kembalilah."},
				0
			)
			return
		end

		if player.quest["subpath_trials"] == 0 then
			player:dialogSeq(
				{
					t,
					"Ah, jadi kau ingin bergabung dengan barisan gerombolan Barbarian? Kuberitahu sekarang juga, itu tidak akan mudah.",
					"Bersiaplah diuji lebih berat daripada seumur hidupmu. Kami Barbarian kaum yang berbangga diri, dan kami tidak menerima sembarang orang ke dalam keluarga tanpa mengetahui kelayakannya lebih dulu."
				},
				1
			)

			local join = player:menuString(
				"Kau yakin ingin memulai prosesnya?",
				{"Ya", "Tidak"}
			)

			if join == "Ya" then
				player.quest["subpath_trials"] = 10
			else
				player:dialogSeq(
					{t, "Baiklah, pergi sana, sampah kota!"},
					0
				)
			end
		end

		if player.quest["subpath_trials"] == 10 then
			-- barb

			if player.quest["barbarian_trial"] == 0 then
				-- trial of willingness

				player:dialogSeq(
					{
						t,
						"Langkah pertama adalah meninggalkan seluruh harta duniawimu. Kami Barbarian kaum yang sederhana, dan kami tidak peduli pada kemewahan busuk penghuni kota, atau \"orang kota\". Kau harus meninggalkan kenyamanan rumahmu dan melepas keluargamu; sebab kalau kau menjadi bagian dari kami, kamilah yang menjadi keluargamu.",
						"Kembalilah kepadaku setelah kau meninggalkan rumahmu dan menanggalkan hidup kota."
					},
					1
				)

				if player.country ~= 0 or player.registry["home"] ~= 0 then
					-- checks to make sure you are aligned with neutral and that you aren't living in any side kingdom areas
					player:dialogSeq(
						{
							t,
							"Kau masih harus meninggalkan harta duniawimu dan menanggalkan hidup kota."
						},
						0
					)
					return
				else
					player:addLegend(
						"Lulus ujian Barbarian: Kerelaan",
						"barbarian_trial_of_willingness",
						16,
						15
					)
					player.quest["barbarian_trial"] = 1
					player:dialogSeq(
						{
							t,
							"Jadi kau sudah mengambil langkah pertama dan meninggalkan hidupmu yang lama. Harus kuakui, aku terkejut senang. Banyak yang tidak sanggup melepas kenyamanan hidup kota. Kulihat kau bersedia mempelajari jalan kami. Tapi percayalah, ujiannya baru saja mulai."
						},
						1
					)
				end
			end

			if player.quest["barbarian_trial"] == 1 then
				--trial of Survival

				if player.quest["barbarian_trial_of_survival"] == 0 then
					player.quest["barbarian_trial_of_survival"] = os.time() + 453600

					-- 126 IRL hours

					player:dialogSeq(
						{
							t,
							"Tidak cukup sekadar tinggal di alam liar. Kau harus menyatu dengannya. Kau harus benar-benar akrab dengan kekuatannya, kelemahannya, hasratnya. Hanya dengan begitu kau bisa dianggap bagian dari gerombolan Barbarian.",
							"Kami Barbarian berbangga pada kemampuan kami bertahan hidup sendiri. Pergilah sekarang ke Alam Liar! Pergilah hanya dengan pakaian di badan dan kapak di tangan. Hiduplah di alam liar, di antara tumbuhan dan binatang, dan bertahanlah hanya dengan akal serta kekuatan kasarmu.",
							"Jangan minta bantuan siapa pun; kau harus melakukannya sendiri. Kalau kau sanggup bertahan dalam tugas berbahaya ini selama 6 pekan, kau boleh kembali kepadaku dan melanjutkan latihanmu."
						},
						1
					)
				else
					-- already started quest
					if os.time() < player.quest["barbarian_trial_of_survival"] then
						-- not enough time has elapsed
						player:dialogSeq(
							{
								t,
								"Kau belum membuktikan kemampuanmu bertahan hidup sendiri di alam liar. Kembalilah kepadaku setelah kau membuktikannya."
							},
							0
						)
						return
					end

					player:addLegend(
						"Lulus ujian Barbarian: Ketahanan Hidup",
						"barbarian_trial_of_survival",
						16,
						15
					)
					player.quest["barbarian_trial_of_survival"] = 0
					player.quest["barbarian_trial"] = 2
					player:dialogSeq(
						{
							t,
							"Kau melewati 6 pekan penuh dan masih hidup?! Meski kulihat agak babak belur. Tidak apa-apa, sedikit kurus itu baik untuk jiwa.",
							"Kau membuktikan diri sebagai penyintas yang tangguh, dan kini selangkah lebih dekat untuk diterima suku kami."
						},
						0
					)
				end
			end

			if player.quest["barbarian_trial"] == 2 then
				-- trial of atonement

				if player.quest["barbarian_trial_of_atonement"] == 0 then
					-- not started quest
					player:dialogSeq(
						{
							t,
							"Tidak ada yang lebih penting bagi seorang Barbarian selain keluarganya! Malam ini kita merayakan cara hidup kita serta kasih dan pengabdian kita satu sama lain.",
							"Pergilah kumpulkan 10 tumpuk penuh daging dari harimau ganas untuk dinikmati sanak kita. Harimau itu ada di Iron Labyrinth di selatan.",
							"Bahaya dalam mengumpulkan santapan khas Barbarian ini justru membuat lapar kami makin terpuaskan."
						},
						1
					)

					player.quest["barbarian_trial_of_atonement"] = 1
				elseif player.quest["barbarian_trial_of_atonement"] == 1 then
					-- started quest

					if player.quest["barbarian_trial_of_atonement_meat_collected"] < 200 then
						if player:hasItem("tiger_meat", 20) ~= true then
							player:dialogSeq(
								{
									t,
									"Kau butuh satu tumpuk penuh daging harimau untuk ditambahkan. Temui aku lagi kalau sudah ada."
								},
								0
							)
							return
						end

						player:removeItem("tiger_meat", 20)
						player.quest[
							"barbarian_trial_of_atonement_meat_collected"
						] = player.quest[
							"barbarian_trial_of_atonement_meat_collected"
						] + 20

						player:dialogSeq(
							{
								t,
								"Terima kasih atas daging harimaunya. Sekarang kau tinggal butuh (" .. (200 - player.quest["barbarian_trial_of_atonement_meat_collected"]) .. ") daging harimau lagi untuk menuntaskan tugas ini."
							},
							1
						)
					end

					if player.quest["barbarian_trial_of_atonement_meat_collected"] >= 200 then
						player.quest[
							"barbarian_trial_of_atonement_meat_collected"
						] = 0
						player.quest["barbarian_trial_of_atonement"] = 0
						player.quest["barbarian_trial"] = 3
						player:addLegend(
							"Lulus ujian Barbarian: Penebusan",
							"barbarian_trial_of_atonement",
							16,
							15
						)
						player:dialogSeq(
							{
								t,
								"Bagus sekali, kerjamu rapi. Daging ini akan memuaskan selera raksasa gerombolan Barbarian. Malam ini kita berpesta!"
							},
							1
						)
					end
				end
			end

			if player.quest["barbarian_trial"] == 3 then
				-- trial of repudiation

				local mobs1 = player:allMythicCaveBosses("dragon")
				local quest = "barbarian_trial_of_repudiation"

				if player.quest["barbarian_trial_of_repudiation"] == 0 then
					player:dialogSeq(
						{
							t,
							"Sebelum tugas berikutnya, sedikit penjelasan. Kami orang Barbarian bukan cuma menentang gaya hidup mewah orang kota. Kami menentang segala yang mereka junjung, termasuk kesetiaan buta pada hierarki yang tidak adil.",
							"Orang kota menganggap kami Barbarian kejam, tidak berperasaan, dan dungu. Tidak ada yang lebih jauh dari kenyataan. Justru merekalah yang bodoh. Pemujaan mereka yang hampir menyimpang terhadap raja, ratu, jenderal, dan penguasa lain membuat mereka tak lebih dari domba malang yang siap disembelih.",
							"Kami lebih suka hidup tanpa hierarki dan bersandar pada musyawarah. Memang masih ada Tetua dan para Pemandu, tetapi pandanglah mereka sebagai penunjuk arah pikiran, bukan diktator atau oligark yang harus diikuti tanpa bertanya.",
							"Mereka adalah anggota masyarakat kami yang paling lama di sini dan paling banyak berbuat bagi kaum kami. Hormatilah mereka, tetapi kau tidak wajib memuja atau menganggap mereka tanpa cela.",
							"Justru dalam hal inilah Barbarian sangat berbeda dari kelompok lain yang akan kau temui di seluruh negeri."
						},
						1
					)

					player.quest["barbarian_trial_of_repudiation"] = 1

					-- get current kill counts

					--player:setQuestKillCounts(quest,mobs1)

					player:dialogSeq(
						{
							t,
							"Salah satu kelompok itu, yang paling menjijikkan, adalah para Dragon. Tidak ada kelompok lain yang sedemikian tersesat oleh tipu daya hierarki dan kekuasaan tangan besi.",
							"Sembuhkan obsesi menyedihkan mereka untuk dikendalikan dengan memenggal kepala para pemimpinnya dan membawanya kepadaku. ((Bunuh 1 dari tiap bos Dragon.))"
						},
						1
					)
				elseif player.quest["barbarian_trial_of_repudiation"] == 1 then
					if not player:killedEnough(mobs1, 1) then
						player:dialogSeq(
							{
								t,
								"Kau tidak mengindahkan kata-kataku. Aku masih menunggumu membunuh tiap bos Dragon."
							},
							0
						)
						return
					end

					-- remove clearquestcounts 2 months from 07/18/19
					player:clearQuestKillCounts(quest, mobs1)

					player.quest["barbarian_trial_of_repudiation"] = 2

					--player:setQuestKillCounts(quest,mobs1)

					player:dialogSeq(
						{
							t,
							"Bagus sekali, kepala-kepala ini akan tampak megah kalau diawetkan dan dipajang di atas perapian keluarga. Meski begitu, aku tidak berharap perbuatanmu mengubah pikiran klan Dragon.",
							"Tidak diragukan lagi, para Dragon kini sibuk mencari orang lain yang akan memberitahu mereka cara hidup. Sebagian, kurasa, memang haus dikuasai.",
							"Malah urusan kita dengan mereka belum selesai. Bawakan kepala 2 pemimpin pengganti mereka. ((Bunuh 1 lagi dari tiap bos Dragon.))"
						},
						1
					)
				elseif player.quest["barbarian_trial_of_repudiation"] == 2 then
					if not player:killedEnough(mobs1, 1) then
						player:dialogSeq(
							{
								t,
								"Kau tidak mengindahkan kata-kataku. Aku masih menunggumu membunuh 1 lagi dari tiap bos Dragon."
							},
							0
						)
						return
					end

					player:clearQuestKillCounts(quest, mobs1)

					player.quest["barbarian_trial_of_repudiation"] = 0
					player.quest["barbarian_trial"] = 4
					player:addLegend(
						"Lulus ujian Barbarian: Penyangkalan",
						"barbarian_trial_of_repudiation",
						16,
						15
					)
					player:dialogSeq(
						{
							t,
							"Sejak awal sudah kuperingatkan bahwa ini tidak mudah, bukan? Tapi kau sampai sejauh ini, jauh lebih jauh daripada kebanyakan orang.",
							"Aku memujimu atas keberanian dan ketetapan hatimu. Namun masih ada satu ujian terakhir sebelum kau bisa dianggap bagian dari kami."
						},
						1
					)
				end
			end

			if player.quest["barbarian_trial"] == 4 then
				-- trial of competency

				if player.quest["barbarian_trial_of_competency"] == 0 then
					-- not started
					player.quest["barbarian_trial_of_competency"] = 1

					player.quest["barbarian_trial_of_competency_prior_wins"] = player.registry[
						"carnageWin"
					]

					player:dialogSeq(
						{
							t,
							"Kami Barbarian bukan orang lemah, kami petarung terlatih. Pergilah dan menangkan 3 acara carnage Riches lagi. Mandikan kapakmu dalam darah musuh orang kota lalu bawa kembali ke sini untuk kuperiksa.",
							"Kalau kau bisa membuktikan kepiawaianmu dalam pertempuran, kau boleh bergabung. Kemenangan sebelum saat ini tidak dihitung."
						},
						1
					)
				elseif player.quest["barbarian_trial_of_competency"] == 1 then
					--started

					local diff = player.registry["carnageWin"] - player.quest[
						"barbarian_trial_of_competency_prior_wins"
					]

					if diff < 3 then
						-- has not received 3 new carny wins
						player:dialogSeq(
							{
								t,
								"Aku masih menunggumu membuktikan kemampuanmu bertempur. Kau harus meraih (" .. (3 - diff) .. ") kemenangan Carnage lagi untuk membuktikan kelayakanmu."
							},
							0
						)
						return
					end

					GhengisKhanNpc.clearQuestLegends(player)

					-- add legend for barbarian (maybe?)

					player:updatePath(10, player.mark)
					broadcast(
						-1,
						"[SUBPATH]: Congratulations to our newest " .. player.classNameMark .. " " .. player.name .. "!"
					)

					player:dialogSeq(
						{
							t,
							"Kau berhasil. Akhirnya latihanmu selesai. Kau sudah menunjukkan kelima unsur terpenting dari cara hidup Barbarian sejati.",
							"Kau menampik godaan hidup kota dan hidup mereka yang boros. Kau menunjukkan keahlianmu bertahan di belantara. Kau membuktikan kesetiaanmu pada keluarga (dan kecintaanmu pada Tiger Meat yang lezat!).",
							"Kau membuktikan keenggananmu pada hierarki. Dan kini, akhirnya, kau memperlihatkan kepiawaianmu yang luar biasa dalam pertempuran.",
							"Kau kini bagian dari kami. Selamat datang di keluarga!"
						},
						0
					)
				end
			end
		end
	end,
}
