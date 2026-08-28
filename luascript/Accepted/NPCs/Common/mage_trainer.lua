MageTrainerNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {}

		if player.class == 0 then
			table.insert(opts, "Become a Mage")
		elseif player.baseClass == 3 then
			if player.level < 99 then
				table.insert(opts, "Divine Secret")
			end
			table.insert(opts, "Pelajari Rahasia")
		end

		table.insert(opts, "Forget Secret")
		table.insert(opts, "Become Noble")
		table.insert(opts, "Tugas Kecil")

		if (player.registryString["minor_quest"] ~= "") then
			table.insert(opts, "Tuntaskan Tugas Kecil")
		end

		if player.baseClass == 3 then
			if player.level >= 66 and player:hasLegend("blessed_by_the_stars") and not player:hasLegend("mastered_the_stars") then
				if player.quest["star_armor"] == 0 or player.quest["star_armor"] == 1 then
					table.insert(opts, "Mage Star 1")
				elseif player.quest["star_armor"] == 2 then
					table.insert(opts, "Mage Star 2")
				elseif player.quest["star_armor"] == 3 then
					table.insert(opts, "Mage Star 3")
				end
			end
		end

		if player.baseClass == 3 then
			if player.level >= 76 and player:hasLegend("mastered_the_stars") and not player:hasLegend("understood_the_moon") and not player:hasLegend("survived_the_sun") then
				if player.quest["moon_armor"] == 0 or player.quest["moon_armor"] == 1 then
					table.insert(opts, "Mage Moon 1")
				elseif player.quest["moon_armor"] == 2 then
					table.insert(opts, "Mage Moon 2")
				elseif player.quest["moon_armor"] == 3 then
					table.insert(opts, "Mage Moon 3")
				elseif player.quest["moon_armor"] == 4 then
					table.insert(opts, "Mage Moon 4")
				elseif player.quest["moon_armor"] == 5 then
					table.insert(opts, "Mage Moon 5")
				end
			end
		end

		if player.baseClass == 3 then
			if player.level >= 86 and player:hasLegend("mastered_the_stars") and player:hasLegend("understood_the_moon") and not player:hasLegend("survived_the_sun") then
				if player.quest["sun_armor"] == 0 or player.quest["sun_armor"] == 1 then
					table.insert(opts, "Mage Sun 1")
				elseif player.quest["sun_armor"] == 2 then
					table.insert(opts, "Mage Sun 2")
				elseif player.quest["sun_armor"] == 3 then
					table.insert(opts, "Mage Sun 3")
				elseif player.quest["sun_armor"] == 4 then
					table.insert(opts, "Mage Sun 4")
				elseif player.quest["sun_armor"] == 5 then
					table.insert(opts, "Mage Sun 5")
				elseif player.quest["sun_armor"] == 6 then
					table.insert(opts, "Mage Sun 6")
				elseif player.quest["sun_armor"] == 7 then
					table.insert(opts, "Mage Sun 7")
				elseif player.quest["sun_armor"] == 8 then
					table.insert(opts, "Mage Sun 8")
				end
			end
		end

		if npc.mapTitle == "Wand" and player.level >= 10 and player.baseClass == 3 and not player:hasLegend("family_nangen_mages") then
			table.insert(opts, "Ward")
		end

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)
		local choice2

		if choice == "Become Noble" then
			if player.level < 75 then
				player:dialogSeq(
					{
						t,
						"Kau masih muda dan belum siap untuk ini. Kembalilah kalau sudah mencapai level 75."
					},
					1
				)
				return
			else
				general_npc_funcs.setTitle(player, npc)
			end
		elseif choice == "Tugas Kecil" then
			MinorQuest.quest(player, npc)
		elseif choice == "Tuntaskan Tugas Kecil" then
			MinorQuest.complete(player, npc)
		elseif choice == "Become a Mage" then
			if player.level < 5 then
				player:dialogSeq(
					{
						t,
						"Salam, anak kecil! Kembalilah kepadaku kalau kau sudah mencapai pencerahan kelima."
					},
					0
				)
				return
			end
			player:dialogSeq(
				{
					t,
					"Salam, yang perkasa! Selamat datang di tempat sucian ku, tempat sucian para pengguna sihir agung.",
					"Kau datang untuk memilih jalurmu? Kurasa kau akan jadi mage yang hebat, sekaligus pahlawan besar."
				},
				1
			)
			choice2 = player:menuString(
				"Maukah kau menempuh jalur mage?",
				{"Ya", "Ceritakan lebih banyak", "Tidak"}
			)
		elseif choice == "Divine Secret" then
			player:futureSpells(npc)
		elseif choice == "Pelajari Rahasia" then
			player:learnSpell(npc)
		elseif choice == "Forget Secret" then
			player:forgetSpell(npc)
		elseif choice == "Strangers" then
			player:sendMinitext("Aku tidak punya apa-apa untukmu, Orang Asing.")
		elseif choice == "Ward" then
			if player.quest["mage_ward"] == 1 then
				if player.quest["zapped_yin_mouse"] == 0 or player.quest["zapped_yang_mouse"] == 0 or player.quest[
					"zapped_void_mouse"
				] == 0 or player.quest["mage_ward_met_ghost"] == 0 or player:hasItem(
					"rose",
					1
				) ~= true or player:hasItem("ore_high", 1) ~= true then
					player:sendMinitext("Kau belum menuntaskan semua tugas yang diberikan kepadamu.")
					return
				end

				player:removeItem("ore_high", 1)
				player:removeItem("rose", 1)

				if not player:hasLegend("family_nangen_mages") then
					player:addLegend(
						"Keluarga para Nangen Mage (" .. curT() .. ")",
						"family_nangen_mages",
						3,
						128
					)
				end

				-- unset values
				player.quest["zapped_yin_mouse"] = 0
				player.quest["zapped_yang_mouse"] = 0
				player.quest["zapped_void_mouse"] = 0
				player.quest["mage_ward_met_ghost"] = 0
				player.quest["mage_ward"] = 0

				player:addItem("magicians_ward", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Kau belajar dengan baik dan telah memperoleh perlindungan para Nangen Mage. Ambil jimat ini, ditempa lama berselang oleh nabi-nabi yang sama yang mengajarimu jalan kami.",
						"Jimat ini bukan hanya menambah kekuatan sihirmu, tetapi juga melindungimu dalam pertempuran mendatang. Hanya ini satu-satunya yang akan kuberikan padamu."
					},
					0
				)

				return
			end

			player:dialogSeq(
				{
					t,
					"Ah, kulihat kau datang mencari pengetahuan para Mage Nagnang."
				},
				1
			)
			local choice = player:menuSeq(
				"Aku bukan pemegang pengetahuan itu, hanya orang yang memberi tahu mereka yang layak ke mana harus mencarinya. Apakah kau orang yang layak?",
				{"Ya, aku layak", "Tidak, aku tidak layak."},
				{}
			)

			if choice == 1 then
				player:dialogSeq(
					{
						t,
						"Kalau begitu akan kuberitahu di mana pengetahuan yang kau cari berada. Di atas gua ini ada gua lain. Di dalamnya tinggal tiga nabi.",
						"Masing-masing mewujudkan satu daya gaib. Satu untuk Yin, satu untuk Yang, dan yang ketiga untuk Kehampaan. Mereka masing-masing akan menilai potensimu lalu memberimu petunjuk.",
						"Kalau kau mengikuti petunjuk mereka dan membuktikan kepadaku bahwa kau terhormat dan bijaksana dengan kembali ke sini setelah menuntaskan seluruh tugas mereka, akan kuberi kau jimat pelindung.",
						"Untuk menemui tiap nabi, kau harus lebih dulu menyerang salah satu tikus dengan mantra lalu mengutuk tikus abadi yang sama. Makhluk itu akan mati sebagai persembahan, dan kau baru bisa masuk.",
						"Hati-hati, kutuklah HANYA SATU makhluk sebelum memasuki tiap ruang. Kalau lebih, para bijak tidak akan berbicara denganmu dan kau harus kembali kepadaku.",
						"Aku juga memohon, dengarkan SEMUANYA dan segala yang mereka katakan. Kalau tidak, jimat itu tidak akan kuberikan."
					},
					1
				)
				player.quest["mage_ward"] = 1
				player:sendMinitext("Semoga berhasil.")
			elseif choice == 2 then
				player:sendMinitext("Aku mengagumi kejujuranmu.")
				return
			end
		end

		if choice2 == "Ya" then
			player:dialogSeq(
				{
					t,
					"Bagus! Itu keputusan yang tepat. Aku melihatmu kelak jadi pahlawan besar di tanah ini. Sekarang biar kubekali kau dengan perlengkapan."
				},
				1
			)

			player:addItem("staff_of_power", 1)

			if player.sex == 0 then
				player:addItem("summer_garb", 1)
				player:addItem("merchant_helm", 1)
			elseif player.sex == 1 then
				player:addItem("summer_dress", 1)
				player:addItem("spring_helmet", 1)
			end

			player:addItem("herb_pipe", 4)

			player:addGold(500)
			player:updatePath(3, 0)
			player:calcStat()

			player:dialogSeq(
				{
					t,
					"Ini zirah dan senjata untukmu. Keduanya khusus jalur penyihir dan akan membantumu memulai.",
					"Kuberi juga sedikit emas, hanya itu yang bisa kusisihkan sekarang. Emas itu akan membantumu memperbaiki barang dan membeli perlengkapan lain seperti cincin.",
					"Kau juga punya empat pipa herbal; benda itu memulihkan manamu. Kalau sudah habis, belilah lagi, para pedagang di kota menjualnya",
					"Kalau kau ingin mempelajari beberapa keahlian, bilang saja. Banyak yang bisa kuajarkan untuk membantumu bertarung."
				},
				1
			)
		elseif choice2 == "Ceritakan lebih banyak" then
			player:dialogSeq(
				{
					t,
					"Bercerita soal mage? Nah, mage adalah pengguna sihir di tanah ini, memadukan sihir serang dan sihir bertahan yang hebat.",
					"Kami memakai sihir untuk menundukkan musuh dan menaklukkan siapa pun yang menghadang. Kekuatan kami juga bisa dipakai bertahan, untuk menyembuhkan dan menyelamatkan diri sendiri maupun orang lain.",
					"Mage adalah pemburu yang mandiri dan mudah berburu sendirian, tetapi selalu lebih baik bergabung dengan yang lain - banyak orang berarti lebih aman!"
				},
				1
			)

			local choice3 = player:menuString(
				"Maukah kau bergabung dengan kami sekarang?",
				{"Ya", "Tidak"}
			)

			if choice3 == "Tidak" then
				player:dialogSeq(
					{
						t,
						"Baiklah, aku menunggu di sini kalau kau berubah pikiran. Aku selalu mencari orang-orang hebat untuk bergabung dengan jalan yang agung ini."
					},
					1
				)
			elseif choice3 == "Ya" then
				player:dialogSeq(
					{
						t,
						"Bagus! Itu keputusan yang tepat. Aku melihatmu kelak jadi pahlawan besar di tanah ini. Sekarang biar kubekali kau dengan perlengkapan."
					},
					1
				)

				player:addItem("staff_of_power", 1)

				if player.sex == 0 then
					player:addItem("summer_garb", 1)
				elseif player.sex == 1 then
					player:addItem("summer_dress", 1)
				end

				player:addItem("spring_helmet", 1)
				player:addItem("herb_pipe", 4)

				player:addGold(500)
				player:updatePath(3, 0)
				player:calcStat()

				player:dialogSeq(
					{
						t,
						"Ini zirah dan senjata untukmu. Keduanya khusus jalur penyihir dan akan membantumu memulai.",
						"Kuberi juga sedikit emas, hanya itu yang bisa kusisihkan sekarang. Emas itu akan membantumu memperbaiki barang dan membeli perlengkapan lain seperti cincin.",
						"Kau juga punya empat pipa herbal; benda itu memulihkan manamu. Kalau sudah habis, belilah lagi, para pedagang di kota menjualnya",
						"Kalau kau ingin mempelajari beberapa keahlian, bilang saja. Banyak yang bisa kuajarkan untuk membantumu bertarung."
					},
					1
				)
			end
		elseif choice2 == "Tidak" then
			player:dialogSeq(
				{
					t,
					"Baiklah, aku menunggu di sini kalau kau berubah pikiran. Aku selalu mencari orang-orang hebat untuk bergabung dengan jalan yang agung ini."
				},
				1
			)
		end

		if choice == "Mage Star 1" then
			local star = {graphic = convertGraphic(428, "item"), color = 0}
			player.npcGraphic = star.graphic
			player.npcColor = star.color
			player.dialogType = 0
			player.lastClick = npc.ID

			if player.registry["flushed_kills"] == 0 then
				player:flushKills("skeleton_mage")
				player:flushKills("skeleton_warrior")
				player.registry["flushed_kills"] = 1
			end

			player.quest["star_armor"] = 1

			player:dialogSeq({t, "Setiap lelaki dan perempuan adalah bintang."}, 1)
			player:dialogSeq({star, "Kau ingin berkelip?"}, 1)
			player:dialogSeq({t, "Semua orang ingin. Namun banyak yang gagal."}, 1)

			if not player:karmaCheck("rabbit") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk menguasai bintang. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("skeleton_mage") >= 1 and player:killCount("skeleton_warrior") >= 1 then
				player.quest["star_armor"] = 2
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)

				return
			end

			player:dialogSeq(
				{
					t,
					"Di antara yang gagal ada Skeleton Mage dan Skeleton Warrior. Bunuh keduanya, lalu kembalilah."
				},
				0
			)
		end

		if choice == "Mage Star 2" then
			if not player:karmaCheck("rabbit") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk menguasai bintang. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Kau punya kecepatan bintang, tetapi punyakah kau kekuatannya? Bawakan aku dua holy ring."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"((Tekan \"Lanjut\" HANYA kalau kau siap barangmu diambil. Kalau tidak, tekan \"Keluar\".))"
				},
				1
			)

			if player:hasItem("holy_ring", 2) ~= true then
				player:dialogSeq(
					{
						t,
						"Cincinnya belum ada. Kembalilah kalau sudah kau punya."
					},
					0
				)
				return
			end

			player:removeItem("holy_ring", 2)
			player.quest["star_armor"] = 3
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Mage Star 3" then
			if not player:karmaCheck("rabbit") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk menguasai bintang. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			local item = {}

			if player.sex == 0 then
				-- male
				item = Item("star_garb")
			elseif player.sex == 1 then
				-- female
				item = Item("star_dress")
			end

			local armor = {graphic = item.icon, color = item.iconC}

			player:dialogSeq(
				{
					t,
					"Untuk benar-benar bersinar dengan cahaya bintang, kau juga harus membawa tongkat yang berpendar oleh cahaya bintang."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"((Tekan \"Lanjut\" HANYA kalau kau siap barangmu diambil. Kalau tidak, tekan \"Keluar\".))"
				},
				1
			)

			if player:hasItem("star_staff", 1) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah punya Star-staff."},
					0
				)
				return
			end

			player:removeItem("star_staff", 1)

			local choice2 = player:dialogSeq(
				{
					armor,
					"Kau ingin mengenakan zirah ini? Harganya sebagian kemampuanmu dan sebagian karmamu."
				},
				1
			)

			if choice2 == true then
				player.baseWill = player.baseWill - 1
				player.karma = player.karma - 1
				player:addItem(item.yname, 1, 0, player.ID)
				player.quest["star_armor"] = 0
				player.registry["flushed_kills"] = 0
				player:addLegend(
					"Menguasai bintang (" .. curT() .. ")",
					"mastered_the_stars",
					5,
					128
				)
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				player:calcStat()
			end
		end

		if choice == "Mage Moon 1" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("li")

				-- in Sute cave
				player:flushKills("white_wolf")

				-- in Buya Fox
				player.registry["flushed_kills"] = 1
			end
			player.quest["moon_armor"] = 1

			player:dialogSeq(
				{t, "Kau kembali untuk memohon bimbingan bulan?"},
				1
			)
			player:dialogSeq(
				{t, "Baiklah, tetapi pengorbanannya akan jauh lebih besar!"},
				1
			)
			player:dialogSeq(
				{t, "Kau menempuh jalur Sihir. Buktikan dirimu."},
				1
			)

			if not player:karmaCheck("ox") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk memahami bulan. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("li") >= 1 then
				player.quest["moon_armor"] = 2
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Seekor binatang keji mencuri cahaya bulan demi kesombongannya. Bunuh monster bernama terpendek di seluruh negeri untuk membebaskan kekuatan bulan."
				},
				1
			)
			player:dialogSeq(
				{t, "Kembalilah kepadaku kalau tugas ini sudah kau selesaikan."},
				0
			)
		end

		if choice == "Mage Moon 2" then
			if not player:karmaCheck("ox") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk memahami bulan. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("white_wolf") >= 1 then
				player.quest["moon_armor"] = 3
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Seekor binatang keji mencuri cahaya bulan demi kesombongannya. Bunuh makhluk terlambat di seluruh negeri untuk membebaskan kekuatan bulan."
				},
				1
			)
			player:dialogSeq(
				{t, "Kembalilah kepadaku kalau tugas ini sudah kau selesaikan."},
				0
			)
		end

		if choice == "Mage Moon 3" then
			if not player:karmaCheck("ox") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk memahami bulan. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Untuk tugas berikutnya, kau harus membawakan satu set kunci lengkap"
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Aku butuh:\nKey to Earth\nKey to Fire\nKey to Heaven\nKey to Mountain\nKey to Wind\nKey to Pond\nKey to Thunder\nKey to Water\nSute's Key"
				},
				1
			)

			local keys = {
				"key_to_earth",
				"key_to_fire",
				"key_to_heaven",
				"key_to_mountain",
				"key_to_wind",
				"key_to_pond",
				"key_to_thunder",
				"key_to_water",
				"sutes_key"
			}

			for i = 1, #keys do
				if player:hasItem(keys[i], 1) ~= true then
					player:dialogSeq(
						{t, "Kembalilah kalau seluruh kuncinya sudah kau punya."},
						0
					)
					return
				end
			end

			for i = 1, #keys do
				player:removeItem(keys[i], 1)
			end

			player.quest["moon_armor"] = 4
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Mage Moon 4" then
			if not player:karmaCheck("ox") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk memahami bulan. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Untuk tugas berikutnya, kau harus membawakan 2 Star-stave dan satu holy ring."
				},
				1
			)

			if player:hasItem("star_staff", 2) ~= true or player:hasItem("holy_ring", 1) ~= true then
				player:dialogSeq(
					{
						t,
						"Kembalilah kepadaku kalau star-stave dan holy ring itu sudah kau punya."
					},
					0
				)
				return
			end

			player:removeItem("star_staff", 2)
			player:removeItem("holy_ring", 1)

			player.quest["moon_armor"] = 5
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Mage Moon 5" then
			if not player:karmaCheck("ox") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk memahami bulan. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			local item = {}
			local armor = {}
			if player.sex == 0 then
				-- male
				armor = Item("star_garb")
				item = Item("moon_garb")
			elseif player.sex == 1 then
				-- female
				armor = Item("star_dress")
				item = Item("moon_dress")
			end

			local armorg = {graphic = item.icon, color = item.iconC}

			player:dialogSeq(
				{
					t,
					"Kekuatan bulan tidak semudah itu ditundukkan! Bawakan aku, dalam keadaan tidak dikenakan, " .. armor.name .. "."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"((Tekan \"Lanjut\" HANYA kalau kau siap barangmu diambil. Kalau tidak, tekan \"Keluar\".))"
				},
				1
			)

			if player:hasItem(armor.yname, 1) ~= true then
				player:dialogSeq(
					{t, "Silakan kembali kalau barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end
			player:removeItem(armor.yname, 1)

			local choice2 = player:dialogSeq(
				{
					armorg,
					"Kau ingin mengenakan zirah ini? Harganya sebagian kemampuanmu dan sebagian karmamu."
				},
				1
			)

			if choice2 == true then
				player.baseGrace = player.baseWill - 2
				player.karma = player.karma - 2
				player:addItem(item.yname, 1, 0, player.ID)
				player.quest["moon_armor"] = 0
				player.registry["flushed_kills"] = 0
				player:addLegend(
					"Memahami bulan (" .. curT() .. ")",
					"understood_the_moon",
					5,
					128
				)
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				player:calcStat()
			end
		end

		if choice == "Mage Sun 1" then
			local caveLevel = player:mythicCaveReqCheck("rabbit")
			local mobs = {}
			local mobAmounts = {}
			local mobName1 = ""
			local mobName2 = ""

			if caveLevel == 2 then
				mobs = {"mad_hare", "giant_rabbit"}
				mobAmounts = {60, 60}
				mobName1 = "Mad hare"
				mobName2 = "Giant rabbit"
			elseif caveLevel == 3 then
				mobs = {"fluff", "thump"}
				mobAmounts = {40, 40}
				mobName1 = "Fluff"
				mobName2 = "Thump"
			end

			if player.registry["flushed_kills"] == 0 then
				for i = 1, #mobs do
					player:flushKills(mobs[i])

					-- ogres
				end
				player.registry["flushed_kills"] = 1
			end

			player.quest["sun_armor"] = 1

			player:dialogSeq(
				{t, "Matahari adalah yang terperkasa dan paling ganas di antara semuanya."},
				1
			)
			player:dialogSeq(
				{t, "Hanya yang terbaik dan paling tulus yang bisa menguasainya."},
				1
			)

			if not player:karmaCheck("tiger") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount(mobs[1]) >= mobAmounts[1] and player:killCount(mobs[2]) >= mobAmounts[
				2
			] then
				player.quest["sun_armor"] = 2
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Aku tidak iri padamu, mage. Sebab untuk membuktikan kelayakanmu, kau harus membunuh " .. mobAmounts[
						1
					] .. " " .. mobName1 .. " dan " .. mobAmounts[2] .. " " .. mobName2 .. "."
				},
				1
			)
		end

		if choice == "Mage Sun 2" then
			local count = 0

			player:dialogSeq(
				{
					t,
					"Berikutnya aku minta tiga barang yang namanya mengandung \"Star\"."
				},
				1
			)

			player:dialogSeq(
				{
					t,
					"((Tekan \"Lanjut\" HANYA kalau kau siap barangmu diambil. Kalau tidak, tekan \"Keluar\".))"
				},
				1
			)

			if not player:karmaCheck("tiger") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			local items = {
				"star_powder",
				"stardrop",
				"star_staff",
				"star_burst"
			}

			for i = 1, #items do
				if player:hasItem(items[i], 1) == true then
					count = count + 1
				end
			end

			if count < 3 then
				player:dialogSeq(
					{
						t,
						"Aku butuh tiga barang yang namanya mengandung kata \"Star\"."
					},
					0
				)
				return
			end

			for i = 1, #items do
				--player:talk(0,""..itemTake[i].." "..itemAmounts[i])
				player:removeItem(items[i], 1)
			end

			player.quest["sun_armor"] = 3
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Mage Sun 3" then
			local mobsToKill = {
				"skeleton_warrior",
				"wild_horse",
				"wild_rooster"
			}

			if player.registry["flushed_kills"] == 0 then
				for i = 1, #mobsToKill do
					player:flushKills(mobsToKill[i])
				end
				player.registry["flushed_kills"] = 1
			end

			player:dialogSeq(
				{
					t,
					"Bunuhlah makhluk yang namanya mengandung kata \"Slow\"."
				},
				1
			)

			if not player:karmaCheck("tiger") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount(mobsToKill[1]) >= 1 or player:killCount(mobsToKill[2]) >= 1 or player:killCount(mobsToKill[3]) >= 1 then
				player.quest["sun_armor"] = 4
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			else
				player:dialogSeq(
					{
						t,
						"Kau belum membunuh makhluk yang namanya mengandung kata \"Slow\"."
					},
					0
				)
			end
		end

		if choice == "Mage Sun 4" then
			player:dialogSeq(
				{
					t,
					"Berikutnya aku minta 20 White Amber, 4 Holy Ring, 5 Star-stave, dan 2 Corrupted stave."
				},
				1
			)

			player:dialogSeq(
				{
					t,
					"((Tekan \"Lanjut\" HANYA kalau kau siap barangmu diambil. Kalau tidak, tekan \"Keluar\".))"
				},
				1
			)

			if not player:karmaCheck("tiger") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:hasItem("white_amber", 20) ~= true or player:hasItem("holy_ring", 4) ~= true or player:hasItem(
				"star_staff",
				5
			) ~= true or player:hasItem("corrupted_staff", 2) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			player:removeItem("white_amber", 20)
			player:removeItem("holy_ring", 4)
			player:removeItem("star_staff", 5)
			player:removeItem("corrupted_staff", 2)

			player.quest["sun_armor"] = 5
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Mage Sun 5" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("massive_scorpion")
				player.registry["flushed_kills"] = 1
			end

			player:dialogSeq(
				{t, "Bunuhlah Massive Scorpion di Kugnae Spider cave."},
				1
			)

			if not player:karmaCheck("tiger") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("massive_scorpion") >= 1 then
				player.quest["sun_armor"] = 6
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			else
				player:dialogSeq(
					{t, "Kau belum membunuh Massive Scorpion."},
					0
				)
			end
		end

		if choice == "Mage Sun 6" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("rabbit")
				player.registry["flushed_kills"] = 1
			end

			player:dialogSeq(
				{t, "Bunuhlah 200 kelinci lalu kembalilah kepadaku."},
				1
			)

			if not player:karmaCheck("tiger") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("rabbit") >= 200 then
				player.quest["sun_armor"] = 7
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			else
				player:dialogSeq(
					{t, "Kau belum membunuh sedikitnya 200 kelinci."},
					0
				)
			end
		end

		if choice == "Mage Sun 7" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("squirrel")
				player.registry["flushed_kills"] = 1
			end

			player:dialogSeq(
				{
					t,
					"Ambilkan aku 14 gold acorn sambil membunuh 200 tupai, lalu kembalilah kepadaku."
				},
				1
			)

			if not player:karmaCheck("tiger") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:hasItem("gold_acorn", 14) ~= true then
				player:dialogSeq({t, "Gold acorn-nya belum ada."}, 0)
				return
			end

			if player:killCount("squirrel") >= 200 then
				player:removeItem("gold_acorn", 14)
				player.quest["sun_armor"] = 8
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			else
				player:dialogSeq(
					{t, "Kau belum membunuh sedikitnya 200 tupai."},
					0
				)
			end
		end

		if choice == "Mage Sun 8" then
			local item = {}
			local armor = {}
			if player.sex == 0 then
				-- male
				armor = Item("moon_garb")
				item = Item("sun_garb")
			elseif player.sex == 1 then
				-- female
				armor = Item("moon_dress")
				item = Item("sun_dress")
			end

			local armorg = {graphic = item.icon, color = item.iconC}

			player:dialogSeq({t, "Tunjukkan busana bulanmu"}, 1)

			player:dialogSeq(
				{
					t,
					"((Tekan \"Lanjut\" HANYA kalau kau siap barangmu diambil. Kalau tidak, tekan \"Keluar\".))"
				},
				1
			)

			if not player:karmaCheck("tiger") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:hasItem(armor.yname, 1) ~= true then
				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			player:removeItem(armor.yname, 1)

			player:dialogSeq(
				{
					t,
					"Kau bertahan melewati banyak ujian. Sebentar lagi ganjaran besar jadi milikmu!"
				},
				1
			)

			local choice2 = player:dialogSeq(
				{
					armorg,
					"Kau ingin mengenakan zirah ini? Ia akan menguras kemampuanmu dan sebagian karmamu."
				},
				1
			)

			if choice2 == true then
				player.baseMight = player.baseMight - 2
				player.baseGrace = player.baseGrace - 2
				player.baseWill = player.baseWill - 3
				player:removeKarma(3)
				player:addItem(item.yname, 1, 0, player.ID)
				player.registry["flushed_kills"] = 0
				player.quest["sun_armor"] = 0
				player:addLegend(
					"Bertahan di bawah matahari (" .. curT() .. ")",
					"survived_the_sun",
					5,
					128
				)
				player:dialogSeq({t, "Itu milikmu."}, 0)
				player:calcStat()
			end
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

		if speech == "sute" then
			Tools.checkKarma(player)

			if player:hasItem("sutes_key", 1) == true and player:hasLegend("slew_mighty_sute") == false then
				player:removeItem("sutes_key", 1)
				player:addLegend(
					"Membunuh Sute yang perkasa (" .. curT() .. ")",
					"slew_mighty_sute",
					5,
					16
				)
				player:giveXP(50000)
				player.registry["sute_quest_dye"] = 0
				player.registry["sute_quest_timer"] = 0
				player:dialogSeq(
					{
						t,
						"Kerjamu bagus dan semua orang akan tahu jerih payahmu. Sayangnya aku baru tahu bahwa jiwanya belum tenang. Sute akan segera terlahir kembali."
					},
					0
				)
				return
			end

			if os.time() < player.registry["sute_quest_timer"] then
				player:dialogSeq(
					{
						t,
						"Waktunya belum cukup. Kalau kuoleskan bubuk lagi sekarang, kau akan mati. Kembalilah nanti."
					},
					0
				)
				return
			end

			--if player:hasLegend("slew_mighty_sute") then
			--	player:dialogSeq({t,"You have already helped me with Sute. thanks again."},0)
			--return
			--end

			if player.level < 28 then
				player:dialogSeq(
					{
						t,
						"Wajah Eldritch tampak muram. 'Kau masih terlalu muda untuk mengetahui hal itu.'"
					},
					0
				)
				return
			end

			local choice = player:menuString(
				"Ya, aku bisa bercerita tentang Sute. Kau ingin mendengar kisah selengkapnya?",
				{"Tolong ceritakan.", "Tidak, katakan saja apa yang harus dilakukan."},
				{}
			)
			local choice2

			if choice == "Tolong ceritakan." then
				player:dialogSeq(
					{
						t,
						"'Ah, Sute...' sang mage mengembuskan napas sedih. 'Sute dulu murid hebatku. Bakat sihirnya luar biasa. Tetapi kepercayaan dirinya berlebihan, kesombongannya pun.'",
						"'Sekitar dua dasawarsa lalu, para Ogre Utara melancarkan serangan mendadak besar-besaran ke Buya. Mereka memakai siasat rumit dan didukung sihir yang aneh.'",
						"Eldritch menatap ke atas, mengenang masa lalu. 'Gerbang Buya sendiri jebol dan kami terpaksa mundur ke istana. Setelah serangan pertama kami bertahan hidup, tetapi tidak sanggup mengalahkan para Ogre.'",
						"'Kami tahu para Ogre pasti disatukan oleh kekuatan yang lebih licik untuk menyerang sedemikian efektif. Kami menduga itu semacam mage yang rusak jiwanya, tetapi tak pernah tahu kebenarannya.'",
						"'Selagi kami menyusun rencana mengatasi ancaman itu, Sute yang tidak sabar, yang baru saja memperoleh pakaian Ancient-nya, berangkat sendirian ke Arctic Land. Sebelum kami sadar ia pergi, para Ogre mundur secara misterius.'",
						"'Kami mengira Sute tewas dan takjub bahwa entah bagaimana ia berhasil. Dua tahun kemudian, sesuatu yang dulunya Sute kembali. Tubuhnya membeku dan ia meracau tak keruan.'",
						"'Seorang poet, Lintong, mencoba menyembuhkannya, tetapi Sute menghantamnya dengan mantra es yang dahsyat.'",
						"'Ketika kami mencoba menundukkannya, Sute mengamuk gila dan kabur ke gua di sisi utara Buya, meski saat itu kami tidak tahu ke mana ia pergi. Ia membentuk semacam pasukan dari makhluk-makhluk ganjil.'",
						"'Anehnya, mereka tidak dipakai menyerang, melainkan menambang perak di gua itu, yang konon ia timbun. Tetapi kami khawatir akan rencana Sute selanjutnya.'",
						"'Beberapa rombongan pahlawan dikirim ke dalam gua untuk mengakhiri penderitaan Sute, tetapi semuanya gagal.'",
						"'Banyak yang tewas. Beberapa bahkan sanggup mengalahkan Sute, tetapi tubuhnya kemudian bangkit lagi. Tidak ada pilihan lain,' kata Eldritch dengan sesal. 'Kusegel Sute dan makhluk ciptaannya di dalam gua itu.'",
						"'Ada kekuatan jahat luar biasa yang mencemari jiwa Sute. Aku ragu kau bisa membuatnya tenang selamanya, tetapi kalau kau cukup berani, akan kubantu kau mencobanya.'",
						"'Aku bisa melumurimu dengan bubuk khusus yang memungkinkanmu memasuki gua Sute. Aku punya satu takaran bubuk, tetapi hanya cukup untuk sekali masuk.'",
						"'Kalau kau keluar dari gua, kau harus dilumuri lagi, dan berbahaya memakai bubuk itu lebih dari sekali dalam sejam. Bubuknya seharga 200 emas.'"
					},
					1
				)

				choice2 = player:menuString(
					"Mau kuoleskan padamu?",
					{"Ya, aku bersedia membayar.", "Tidak, terima kasih."},
					{}
				)
			elseif choice == "Tidak, katakan saja apa yang harus dilakukan." then
				player:dialogSeq(
					{
						t,
						"'Ada kekuatan jahat luar biasa yang mencemari jiwa Sute. Aku ragu kau bisa membuatnya tenang selamanya, tetapi kalau kau cukup berani, akan kubantu kau mencobanya.'",
						"'Aku bisa melumurimu dengan bubuk khusus yang memungkinkanmu memasuki gua Sute. Aku punya satu takaran bubuk, tetapi hanya cukup untuk sekali masuk.'",
						"'Kalau kau keluar dari gua, kau harus dilumuri lagi, dan berbahaya memakai bubuk itu lebih dari sekali dalam sejam. Bubuknya seharga 200 emas.'"
					},
					1
				)
				choice2 = player:menuString(
					"Mau kuoleskan padamu?",
					{"Ya, aku bersedia membayar.", "Tidak, terima kasih."},
					{}
				)
			end

			if (choice2 == "Ya, aku bersedia membayar.") then
				if player.money < 200 then
					player:dialogSeq(
						{t, "Maaf, emasmu tidak cukup."},
						0
					)
					return
				end

				local armor = player:getEquippedItem(EQ_ARMOR)

				if armor == nil then
					player:dialogSeq(
						{t, "Kau harus mengenakan zirah agar aku bisa mewarnaimu."},
						0
					)
					return
				end

				player:removeGold(200)
				player.registry["sute_quest_dye"] = 1
				player.registry["sute_quest_timer"] = os.time() + 86400

				-- 1 day
				player.armorColor = 26

				player:dialogSeq(
					{
						t,
						"Bubuk itu mengubah warna pakaianmu jadi aneh.",
						"Kalau kau berhasil membunuh Sute, kembalilah kepadaku dan akan kupastikan jerih payahmu diakui."
					},
					1
				)

				player:sendStatus()
				player:updateState()
			end
		elseif speech == "misi" or speech == "kecil" or speech == "misi kecil" then
			Tools.checkKarma(player)
			MinorQuest.quest(player, npc)
		elseif speech == "selesai" or speech "complete quest" then
			Tools.checkKarma(player)
			MinorQuest.complete(player, npc)
		end
	end)
}
