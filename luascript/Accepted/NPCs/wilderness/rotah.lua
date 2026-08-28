local _waypointId = "wilderness"

RotahNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local opts = {
			"Become Neutral",
			"Forgotten past",
			"Ring dan Tribe",
			"Wisdom clothes",
			"Shadow Stats",
			"Mass Exchange",
			"Broadcast Event",
			"Checks",
			"Simpanan",
			"Perjalanan",
			"Tanggal & Waktu"
		}

		if os.time() >= player.registry["gave_fragile_orb_of_world_shout_time"] then
			table.insert(opts, "World Shout Gratis")
		end

		if (not Waypoint.isEnabled(player, _waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)

		if choice == "Become Neutral" then
			-- become neutral
			general_npc_funcs.moveToCountry(player, npc, 0)
		elseif choice == "Forgotten past" then
			-- forgotten past (quest line)
			RotahNpc.forgottenPast(player, npc)
		elseif choice == "Ring dan Tribe" then
			RotahNpc.ringsAndTribes(player, npc)
		elseif choice == "Wisdom clothes" then
			general_npc_funcs.wisdomClothes(player, npc)
		elseif choice == "Shadow Stats" then
			ExpSellerNpc.showShadowMainMenu(player, npc)
		elseif choice == "Mass Exchange" then
			-- max exch
			general_npc_funcs.massExchange(player, npc)
		elseif choice == "Broadcast Event" then
			-- broadcast event
			general_npc_funcs.broadcastEvent(player, npc)
		elseif choice == "Checks" then
			-- checks
			general_npc_funcs.checks(player, npc)
		elseif choice == "Simpanan" then
			bank.show_main_menu(player, npc)
		elseif choice == "Perjalanan" then
			Waypoint.click(player, npc)
		elseif choice == "Tanggal & Waktu" then
			general_npc_funcs.time(player)
		elseif choice == "World Shout Gratis" then
			general_npc_funcs.freeWorldShout(player, npc)
		elseif choice == "Waypoint" then
			Waypoint.add(player, npc, _waypointId)
		end
	end),

	forgottenPast = function(player, npc)
		Tools.configureDialog(player, npc)

		if player:hasLegend("forged_orb") then
			player:dialogSeq({"Kau sudah pernah menempa satu bola."}, 0)
			return
		end

		player:dialogSeq(
			{
				"Kau ingin tahu masa laluku?\n\nKenapa menanyakan hal sekonyol itu?",
				"Aku hanya orang tua; tidak pernah terjadi apa-apa padaku.\n\nAku cuma duduk di sini, mendengarkan bisikan angin..."
			},
			1
		)

		if player.quest["forgotten_path"] == 0 then
			player.quest["forgotten_path"] = 1
			return
		end
	end,

	becomeNeutral = function(player, npc)
		Tools.configureDialog(player, npc)

		player:dialogSeq({"Belum tersedia. Coba lagi nanti."}, 0)

		if player.country == 1 or player.country == 2 then
			player:dialogSeq(
				{
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
				player.clan = 0
				player:sendStatus()
				player:dialogSeq({"Selamat datang di belantara."}, 0)
				return
			end
		elseif player.country == 0 then
			--already neutral
			player:dialogSeq({"Ah, hidup bebas. Bukankah hebat?"}, 0)
			return
		end
	end,

	ringsAndTribes = function(player, npc)
		Tools.configureDialog(player, npc)

		if player.clan ~= 0 then
			player:dialogSeq({"Ini hanya untuk anggota ring dan tribe; kau bagian dari klan resmi sehingga tidak bisa memakai pilihan ini."}, 0)
			return
		end

		player:dialogSeq(
			{
				"Kau boleh mendirikan ring-mu sendiri kapan saja, asalkan kau bukan bagian dari ring, tribe, atau klan lain, dan tidak pernah bermasalah dengan hukum.",
				"Ring adalah kelompok kecil beranggotakan sedikitnya 10 orang. Kalau sewaktu-waktu anggotanya kurang dari 10, ring itu ditutup.",
				"Begitu ring tumbuh sampai 50 anggota, kau berhak membentuk tribe, yang punya lebih banyak kuasa dan pilihan daripada ring kecil.",
				"Terakhir, setelah anggotanya lebih dari 100, kau bisa mengajukan permohonan kepada tribunal klan negerimu, kecuali kalau itu tribe belantara, untuk menjadi klan resmi.",
				"Untuk mendirikan ring-mu, kau butuh 10 anggota, yaitu kau dan sedikitnya 9 orang lain yang hadir bersamaan, serta 500.000 emas.",
				"Begitu kau berhak menjadi tribe, kau butuh 5.000.000 lagi untuk naik tingkat. Tapi pastikan anggotamu tetap di atas 50.\nKalau turun di bawah 50, statusmu kembali jadi ring dan kau harus membayar lagi untuk naik tingkat.",
				"Tiap tingkat, ring, tribe, dan klan punya kemampuan dan kuasanya sendiri. Makin tinggi kelompokmu tumbuh, makin besar kuasa yang bisa kau pakai.",
				"Ring menempatkan pendirinya sebagai primogen, punya satu tingkat dewan, dan kemampuan klan dasar.",
				"Tribe bisa memperoleh simpanan bersama serta dewan tingkat kedua."
			},
			1
		)

		if player.country == 0 then
			-- neutral
			player:dialogSeq({"Karena kau berasal dari kerajaan netral, kau tidak akan pernah bisa menjadi klan resmi; hanya ring dan tribe dari salah satu kota utama yang bisa."}, 1)
		else
			player:dialogSeq({"Kalau kau cukup beruntung menjadi klan resmi lewat tribunal, kau memperoleh balai klan serta kemampuan memperluasnya dan menambah kemampuan baru."}, 1)
		end

		player:dialogSeq(
			{
				"Saat membentuk ring, namanya tidak boleh menyinggung, atau dimaksudkan meniru maupun menghina orang lain.",
				"Nama ring-mu harus khas. Kalau terlalu mirip dengan ring atau klan yang sudah ada, ia bisa dibubarkan tanpa pengembalian biaya.",
				"Kalau kau membentuk ring dengan nama yang tidak pantas, ring itu akan dibubarkan tanpa pengembalian biaya.",
				--"((If you are unsure whether your ring's name is appropriate, please send us a ticket at https://www.retrotk.com/helpdesk))"
			},
			0
		)
	end,

	onSayClick = async(function(player, npc)
		Tools.configureDialog(player, npc)
		local speech = string.lower(player.speech)
		local pathQuest

		if speech == "bunga musim panas manis" and player.quest["forgotten_path"] == 1 then
			player.quest["forgotten_path"] = 2
			player:dialogSeq(
				{
					"Apa katamu?\nHmm.. ya, kadang aku bisa mencium bunga musim panas dalam embusan angin.",
					"Ohh--bau itu mengingatkanku padanya. Ia selalu berbau bunga yang manis, tetapi sayangnya ia tidak pernah lagi ke sini.",
					"Hidup di belantara tidak pernah sanggup ia jalani."
				},
				1
			)

			return
		end

		if speech == "kehidupan liar" and (player.quest["forgotten_path"] == 2 or player.quest["forgotten_path"] == 3) then
			player.quest["forgotten_path"] = 3
			player:dialogSeq(
				{
					"Ahh ya, hidup belantara...\nDi sini tidak banyak orang yang bisa ia tolong.",
					"Ia pindah ke Buya; ia terus bilang ia hanya ingin menolong orang dengan sihir penyembuhnya."
				},
				1
			)

			return
		end

		if speech == "logam aneh" and player.quest["forgotten_path"] == 10 then
			player.quest["forgotten_path"] = 11
			player:dialogSeq(
				{
					"Umm.. apa yang kau bawa ini?",
					"Wah.. logam yang kau bawa ini aneh.\n\nKatamu ia bisa dijadikan Metal orb?",
					"Wah, sungguh? Aku harus memberitahu saudara-saudaraku!"
				},
				1
			)
			return
		end

		if speech == "bola elemen" then
			if player:hasItem("shu_jing", 1) ~= true and player.quest["forgotten_path"] == 11 then
				player:dialogSeq(
					{
						"Hmm.. berhubung kau membantuku dan para Geomancer membuat bola terakhir, mungkin aku bisa membantumu membuat satu.",
						"Lebih dulu tunjukkan seberapa dalam kau memahami ajaranku...\n\nKalau tidak, bagaimana aku bisa memercayaimu?",
						"Bawakan dulu satu Shu jing, lalu akan kuajukan lima pertanyaan, satu untuk tiap unsur yang ada."
					},
					1
				)

				return
			end
			if player:hasItem("shu_jing", 1) == true and player.quest["forgotten_path"] == 11 then
				player:dialogSeq(
					{
						"Hmm.. berhubung kau membantuku dan para Geomancer membuat bola terakhir, mungkin aku bisa membantumu membuat satu.",
						"Lebih dulu tunjukkan seberapa dalam kau memahami ajaranku...\n\nKalau tidak, bagaimana aku bisa memercayaimu?",
						"Bawakan dulu satu Shu jing, lalu akan kuajukan lima pertanyaan, satu untuk tiap unsur yang ada."
					},
					1
				)

				local pathQuest = player:inputSeq(
					"Which element is the beginning of new life?",
					"The element",
					"is the beginning of new life.",
					{},
					{}
				)
				if string.lower(pathQuest) ~= "wood" then
					return
				end

				pathQuest = player:inputSeq(
					"Which element represents the Kun trigram?",
					"The element",
					"represents Kun.",
					{},
					{}
				)
				if string.lower(pathQuest) ~= "earth" then
					return
				end

				pathQuest = player:inputSeq(
					"Which element contains the most Yang?",
					"The element",
					"contains the most Yang.",
					{},
					{}
				)
				if string.lower(pathQuest) ~= "fire" then
					return
				end

				pathQuest = player:inputSeq(
					"Which element represents the Kan Trigram?",
					"The element",
					"represents Kan.",
					{},
					{}
				)
				if string.lower(pathQuest) ~= "water" then
					return
				end

				pathQuest = player:inputSeq(
					"Which element is the most commonly used remedy for the negative Earth energies?",
					"The element",
					"is most often used.",
					{},
					{}
				)
				if string.lower(pathQuest) ~= "metal" then
					return
				end

				player.quest["forgotten_path"] = 12
				player:dialogSeq({"Correct!"}, 0)
				return
			end

			if player.quest["forgotten_path"] == 12 then
				pathQuest = player:inputSeq(
					"Which type of orb would you like to make?",
					"An orb made of",
					"is the one for me.",
					{},
					{}
				)
				local answer = string.lower(pathQuest)
				local mats = {}
				local amts = {}
				if answer == "wood" then
					pathQuest = player:menuString(
						"Untuk membuat Wood orb aku butuh 25 wood scrap, satu star drop, dan 2 yellow amber.\n\nKau siap menukar?",
						{"Ya", "Tidak"},
						{}
					)
					if string.lower(pathQuest) == "yes" then
						mats = {"wood_scraps", "stardrop", "yellow_amber"}
						amts = {25, 1, 2}
					end
				elseif answer == "earth" then
					pathQuest = player:menuString(
						"Untuk membuat Earth orb aku butuh 25 poor ore, satu star drop, dan 2 yellow amber.\n\nKau siap menukar?",
						{"Ya", "Tidak"},
						{}
					)
					if string.lower(pathQuest) == "yes" then
						mats = {"ore_poor", "stardrop", "yellow_amber"}
						amts = {25, 1, 2}
					end
				elseif answer == "fire" then
					pathQuest = player:menuString(
						"Untuk membuat Fire orb aku butuh 10 hot coal, satu star drop, dan 2 yellow amber.\n\nKau siap menukar?",
						{"Ya", "Tidak"},
						{}
					)
					if string.lower(pathQuest) == "yes" then
						mats = {"hot_coal", "stardrop", "yellow_amber"}
						amts = {10, 1, 2}
					end
				elseif answer == "water" then
					pathQuest = player:menuString(
						"Untuk membuat Water orb aku butuh 2 ice shard, 5 star drop, dan 2 yellow amber.\n\nKau siap menukar?",
						{"Ya", "Tidak"},
						{}
					)
					if string.lower(pathQuest) == "yes" then
						mats = {"ice_shard", "stardrop", "yellow_amber"}
						amts = {2, 5, 2}
					end
				elseif answer == "metal" then
					pathQuest = player:menuString(
						"Untuk membuat Metal orb aku butuh 10 metal, satu star drop, dan 2 yellow amber.\n\nKau siap menukar?",
						{"Ya", "Tidak"},
						{}
					)
					if string.lower(pathQuest) == "yes" then
						mats = {"metal", "stardrop", "yellow_amber"}
						amts = {10, 1, 2}
					end
				end

				if string.lower(pathQuest) ~= "yes" then
					return
				end

				for i = 1, #mats do
					if player:hasItem(mats[i], amts[i]) ~= true then
						player:dialogSeq({"Barang yang diperlukan belum lengkap."}, 0)
						return
					end
				end

				for i = 1, #mats do
					player:removeItem(mats[i], amts[i], 9)
				end

				player:addItem(answer .. "_orb", 1, 0, player.ID)

				local questTag = answer:gsub("^%l", string.upper)
				player.quest["forgotten_path"] = 0
				player:addLegend(
					"Menempa bola " .. questTag .. ", " .. curT(),
					"forged_orb",
					6,
					128
				)
			end
		end

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, _waypointId)) then
			Waypoint.add(player, npc, _waypointId)
			return
		end

		Waypoint.onSayClick(player, npc)
	end)
}
