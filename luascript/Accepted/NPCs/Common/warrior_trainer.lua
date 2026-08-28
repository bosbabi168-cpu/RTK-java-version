local _showShieldDialog = function(player)
	player:dialogSeq(
		{
			"Siapa pun yang mengabdikan hidupnya pada senjata harus belajar memakai perisai.",
			"Tetapi pertama-tama, buktikan itu kepadaku. Di Barat dan Utara sini ada sebuah gua. Itulah gua latihan para Warrior kami.",
			"Di dalamnya kau akan menemukan berbagai makhluk berwarna. Kau tidak boleh membunuh yang merah dan biru; hindari mereka.",
			"Di ujung gua ada patung Chung Ryong. Kalau kau mencapainya tanpa membunuh satu pun binatang Biru atau Merah, kau akan diganjar sebuah perisai."
		},
		1
	)
end

WarriorTrainerNpc = {
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
			table.insert(opts, "Become a Warrior")
		elseif player.baseClass == 1 then
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

		if player.baseClass == 1 then
			if player.level >= 66 and player:hasLegend("blessed_by_the_stars") and not player:hasLegend("mastered_the_stars") then
				if player.quest["star_armor"] == 0 or player.quest["star_armor"] == 1 then
					table.insert(opts, "Warrior Star 1")
				elseif player.quest["star_armor"] == 2 then
					table.insert(opts, "Warrior Star 2")
				elseif player.quest["star_armor"] == 3 then
					table.insert(opts, "Warrior Star 3")
				end
			end
		end

		if player.baseClass == 1 then
			if player.level >= 76 and player:hasLegend("mastered_the_stars") and not player:hasLegend("understood_the_moon") and not player:hasLegend("survived_the_sun") then
				if player.quest["moon_armor"] == 0 or player.quest["moon_armor"] == 1 then
					table.insert(opts, "Warrior Moon 1")
				elseif player.quest["moon_armor"] == 2 then
					table.insert(opts, "Warrior Moon 2")
				elseif player.quest["moon_armor"] == 3 then
					table.insert(opts, "Warrior Moon 3")
				elseif player.quest["moon_armor"] == 4 then
					table.insert(opts, "Warrior Moon 4")
				end
			end
		end

		if player.baseClass == 1 then
			if player.level >= 86 and player:hasLegend("mastered_the_stars") and player:hasLegend("understood_the_moon") and not player:hasLegend("survived_the_sun") then
				if player.quest["sun_armor"] == 0 or player.quest["sun_armor"] == 1 then
					table.insert(opts, "Warrior Sun 1")
				elseif player.quest["sun_armor"] == 2 then
					table.insert(opts, "Warrior Sun 2")
				elseif player.quest["sun_armor"] == 3 then
					table.insert(opts, "Warrior Sun 3")
				elseif player.quest["sun_armor"] == 4 then
					table.insert(opts, "Warrior Sun 4")
				elseif player.quest["sun_armor"] == 5 then
					table.insert(opts, "Warrior Sun 5")
				elseif player.quest["sun_armor"] == 6 then
					table.insert(opts, "Warrior Sun 6")
				end
			end
		end

		if npc.mapTitle == "Sword" and player.level >= 10 and player.baseClass == 1 and not player:hasLegend("nagnang_warrior_trial") then
			if player.quest["nagnang_warrior_trial"] == 0 then
				table.insert(opts, "Strangers")
			elseif player.quest["nagnang_warrior_trial"] == 1 then
				table.insert(opts, "Shield")
			end
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
		elseif choice == "Become a Warrior" then
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
					"Salam, yang perkasa! Selamat datang di tempat sucianku, tempat sucian petarung paling perkasa.",
					"Kau datang untuk memilih jalurmu? Kurasa kau akan jadi warrior yang hebat, sekaligus pahlawan besar."
				},
				1
			)
			choice2 = player:menuString(
				"Maukah kau menempuh jalur warrior?",
				{"Ya", "Ceritakan lebih banyak", "Tidak"}
			)
		elseif choice == "Divine Secret" then
			if npc.mapTitle == "Sword" or npc.mapTitle == "Kwi-Sin Sword" or npc.mapTitle == "Ming-Ken Sword" or npc.mapTitle == "Ohaeng Sword" then
				player:futureSpells(npc, {"feral_berserk_warrior"})
			else
				player:futureSpells(npc)
			end
		elseif choice == "Pelajari Rahasia" then
			if npc.mapTitle == "Sword" or npc.mapTitle == "Kwi-Sin Sword" or npc.mapTitle == "Ming-Ken Sword" or npc.mapTitle == "Ohaeng Sword" then
				player:learnSpell(npc, {"feral_berserk_warrior"})
			else
				player:learnSpell(npc)
			end
		elseif choice == "Forget Secret" then
			player:forgetSpell(npc)
		elseif choice == "Strangers" then
			local mobs = {
				"red_deer",
				"red_doe",
				"red_rabbit",
				"blue_deer",
				"blue_doe",
				"blue_rabbit"
			}

			if player:hasItem("green_squirrel_pelt", 1) ~= true then
				player:sendMinitext("Eh? Jangan ganggu aku.")
				player:sendMinitext("Kau bahkan mungkin tidak sanggup membunuh satu pun tupai Hijau di selatan.")
				return
			end
			player:removeItem("green_squirrel_pelt", 1)
			player.quest["nagnang_warrior_trial"] = 1

			for i = 1, #mobs do
				player:flushKills(mobs[i])
			end

			_showShieldDialog(player)
		elseif choice == "Shield" then
			_showShieldDialog(player)
		end

		if choice2 == "Ya" then
			player:dialogSeq(
				{
					t,
					"Bagus! Itu keputusan yang tepat. Aku melihatmu kelak jadi pahlawan besar di tanah ini. Sekarang biar kubekali kau dengan perlengkapan."
				},
				1
			)

			player:addItem("sword_of_power", 1)
			player:addItem("bears_liver", 25)
			if player.sex == 0 then
				player:addItem("jade_scale_mail", 1)
				player:addItem("merchant_helm", 1)
			elseif player.sex == 1 then
				player:addItem("summer_mail_dress", 1)
				player:addItem("spring_helmet", 1)
			end

			player:addGold(500)
			player:updatePath(1, 0)
			player:calcStat()

			player:dialogSeq(
				{
					t,
					"Ini zirah dan senjata untukmu. Keduanya khusus jalur prajurit dan akan membantumu memulai.",
					"Kuberi juga sedikit emas, hanya itu yang bisa kusisihkan sekarang. Emas itu akan membantumu memperbaiki barang dan membeli perlengkapan lain seperti cincin.",
					"Kuberi juga beberapa hati beruang; benda itu menjaga kekuatanmu. Makanlah satu saat kau lemah dan hampir mati. Para pedagang di kota menjualnya kalau kau butuh lagi.",
					"Kalau kau ingin mempelajari beberapa keahlian, bilang saja. Banyak yang bisa kuajarkan untuk membantumu bertarung."
				},
				1
			)
		elseif choice2 == "Ceritakan lebih banyak" then
			player:dialogSeq(
				{
					t,
					"Bercerita soal warrior? Mereka yang terbesar di antara kelas petarung. Pasukan seorang diri, boleh dibilang. Warrior itu ganas, kuat, dan bisa melawan banyak musuh sekaligus.",
					"Warrior sedikit memakai sihir; kami lebih suka keahlian, misalnya kemampuan memukul lebih dari satu makhluk sekaligus.",
					"Kami bergantung pada keahlian menyembuhkan jalur lain, seperti poet, tetapi mereka selalu bersedia bergrup dengan warrior demi kemampuan membunuh kami yang dahsyat."
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

				player:addItem("sword_of_power", 1)
				player:addItem("bears_liver", 25)

				if player.sex == 0 then
					player:addItem("jade_scale_mail", 1)
					player:addItem("merchant_helm", 1)
				elseif player.sex == 1 then
					player:addItem("summer_mail_dress", 1)
					player:addItem("spring_helmet", 1)
				end

				player:addGold(500)
				player:updatePath(1, 0)
				player:calcStat()

				player:dialogSeq(
					{
						t,
						"Ini zirah dan senjata untukmu. Keduanya khusus jalur prajurit dan akan membantumu memulai.",
						"Kuberi juga sedikit emas, hanya itu yang bisa kusisihkan sekarang. Emas itu akan membantumu memperbaiki barang dan membeli perlengkapan lain seperti cincin.",
						"Kuberi juga beberapa hati beruang; benda itu menjaga kekuatanmu. Makanlah satu saat kau lemah dan hampir mati. Para pedagang di kota menjualnya kalau kau butuh lagi.",
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

		if choice == "Warrior Star 1" then
			local star = {graphic = convertGraphic(428, "item"), color = 0}
			player.npcGraphic = star.graphic
			player.npcColor = star.color
			player.dialogType = 0
			player.lastClick = npc.ID

			if player.registry["flushed_kills"] == 0 then
				player:flushKills("spry_monkey")
				player:flushKills("agile_monkey")
				player:flushKills("fast_monkey")
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

			if player:killCount("spry_monkey") >= 18 or player:killCount("agile_monkey") >= 18 or player:killCount("fast_monkey") >= 18 then
				player.quest["star_armor"] = 2
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Di antara yang gagal ada para monyet jahat. Bahkan yang paling gesit pun kekurangan cahaya. Bunuh 18 monyet tercepat, lalu kembalilah."
				},
				0
			)
		end

		if choice == "Warrior Star 2" then
			player:dialogSeq(
				{
					t,
					"Kau punya kecepatan bintang, tetapi punyakah kau kekuatannya? Bawakan aku dua titanium glove."
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

			if player:hasItem("titanium_glove", 2) ~= true then
				player:dialogSeq(
					{
						t,
						"Sarung tangannya belum ada. Kembalilah kalau sudah kau punya."
					},
					0
				)
				return
			end

			player:removeItem("titanium_glove", 2)
			player.quest["star_armor"] = 3
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Warrior Star 3" then
			local item = {}

			if player.sex == 0 then
				-- male
				item = Item("star_scale_mail")
			elseif player.sex == 1 then
				-- female
				item = Item("star_mail_dress")
			end

			local armor = {graphic = item.icon, color = item.iconC}

			player:dialogSeq(
				{
					t,
					"Untuk benar-benar bersinar dengan cahaya bintang, kau juga harus membawa pedang yang berpendar oleh cahaya bintang."
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

			if player:hasItem("electra", 1) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah punya electra."},
					0
				)
				return
			end

			player:removeItem("electra", 1)

			local choice2 = player:dialogSeq(
				{
					armor,
					"Kau ingin mengenakan zirah ini? Harganya sebagian kemampuanmu dan sebagian karmamu."
				},
				1
			)

			if choice2 == true then
				player.baseMight = player.baseMight - 1
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

		if choice == "Warrior Moon 1" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("boar_champion")

				-- pig 1
				player:flushKills("pig_champion")

				-- pig 2
				player:flushKills("pig_avenger")

				-- pig 3
				player:flushKills("mad_dog")

				-- dog 1
				player:flushKills("crazed_mongrel")

				-- dog 2
				player:flushKills("frothing_mutt")

				-- dog 3
				player:flushKills("grim_ogre")
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
				{t, "Kau menempuh jalur Valor. Buktikan dirimu."},
				1
			)

			if not player:karmaCheck("dog") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk memahami bulan. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("boar_champion") >= 1 or player:killCount("pig_champion") >= 1 or player:killCount("pig_avenger") >= 1 then
				player.quest["moon_armor"] = 2
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Seekor binatang keji mencuri cahaya bulan demi kesombongannya. Bunuh manusia-babi yang bercahaya itu untuk membebaskan kekuatan bulan."
				},
				1
			)
		end

		if choice == "Warrior Moon 2" then
			if not player:karmaCheck("dog") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk memahami bulan. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("mad_dog") >= 30 or player:killCount("crazed_mongrel") >= 30 or player:killCount("frothing_mutt") >= 30 then
				player.quest["moon_armor"] = 3
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Anjing-anjing paling gila itu dirusak oleh kekuatan bulan. Bunuh tiga puluh ekor untuk menyadari kekuatan bulan."
				},
				1
			)
		end

		if choice == "Warrior Moon 3" then
			if not player:karmaCheck("dog") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk memahami bulan. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("grim_ogre") >= 20 then
				if player:hasItem("amber", 20) ~= true then
					player:dialogSeq(
						{t, "Kembalilah kalau kedua puluh amber-nya sudah kau punya."},
						0
					)
					return
				end

				player:removeItem("amber", 20)
				player.quest["moon_armor"] = 4
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			else
				player:dialogSeq(
					{t, "Aku tidak melihat darah ogre di bilahmu."},
					1
				)
			end

			player:dialogSeq(
				{
					t,
					"Bulan purnama menetes ke bumi, meresap ke dalam tanah. Para ogre suram berusaha menguasai kekuatan itu. Bunuh dua puluh ekor dan bawakan aku amber sebanyak itu pula."
				},
				1
			)
		end

		if choice == "Warrior Moon 4" then
			if not player:karmaCheck("dog") then
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
				armor = Item("star_scale_mail")
				item = Item("moon_scale_mail")
			elseif player.sex == 1 then
				-- female
				armor = Item("star_mail_dress")
				item = Item("moon_mail_dress")
			end

			if armorequip ~= nil then
				if armorequip.id == armor.id then
					wearing = true
				end
			end

			local armorg = {graphic = item.icon, color = item.iconC}

			player:dialogSeq(
				{
					t,
					"Kekuatan bulan tidak semudah itu ditundukkan! Bawakan sekaligus: satu titanium glove, tiga electra, dan star mail-mu."
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

			if (player:hasItem("titanium_glove", 1) ~= true or
				player:hasItem("electra", 3) ~= true or
				player:hasItem(armor.yname, 1) ~= true) then

				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			player:removeItem(armor.yname, 1)
			player:removeItem("titanium_glove", 1)
			player:removeItem("electra", 3)

			local choice2 = player:dialogSeq(
				{
					armorg,
					"Kau ingin mengenakan zirah ini? Harganya sebagian kemampuanmu dan sebagian karmamu."
				},
				1
			)

			if choice2 == true then
				player.baseMight = player.baseMight - 2
				player.baseGrace = player.baseGrace - 1
				player.karma = player.karma - 2
				player:addItem(item.yname, 1, 0, player.ID)
				player.registry["flushed_kills"] = 0
				player.quest["moon_armor"] = 0
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

		if choice == "Warrior Sun 1" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("frost_ogre")

				-- ogres
				player:flushKills("ice_ogre")

				-- ogres
				player:flushKills("rabbit")

				-- regular shit rabbits
				player:flushKills("squirrel")
				player:flushKills("mythic_monkey")

				-- monkey 1
				player:flushKills("monkey_mauler")

				-- monkey 1
				player:flushKills("divine_monkey")

				-- monkey 2
				player:flushKills("monkey_basher")

				-- monkey 2
				player:flushKills("spirit_monkey")

				-- monkey 3
				player:flushKills("monkey_avenger")

				-- monkey 3
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

			if player:killCount("frost_ogre") >= 60 and player:killCount("ice_ogre") >= 60 then
				player.quest["sun_armor"] = 2
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Jauh di utara ada tanah yang hanya disenggol matahari. Kuasai tanah itu. Bunuh 60 ogre es dan 60 ogre beku, lalu kembalilah."
				},
				1
			)
		end

		if choice == "Warrior Sun 2" then
			if not player:karmaCheck("tiger") then
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
					"Sekarang bawakan aku 20 amber termurni supaya kita bisa menangkap cahaya matahari."
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

			if player:hasItem("white_amber", 20) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau amber-nya sudah kau punya."},
					0
				)
				return
			end

			player:removeItem("white_amber", 20)
			player.quest["sun_armor"] = 3
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Warrior Sun 3" then
			if not player:karmaCheck("tiger") then
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
					"Sekarang kau harus membawakan beberapa hal untuk melengkapi zirahmu. Baca baik-baik dan jangan kembali sebelum SEMUANYA kau punya.",
					"Betapa rapuh tanganmu. Daging dan tulang saja tidak cukup. Bawakan dua titanium glove.",
					"Dan untuk memotong zirahmu dari matahari? Aku butuh empat electra.",
					"Hanya melalui yang tercemar kemurnian sejati bisa dicapai. Kau juga harus membawakan dua corrupted blade."
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

			if (player:hasItem("titanium_glove", 2) ~= true or
				player:hasItem("corrupted_blade", 2) ~= true or
				player:hasItem("electra", 4) ~= true) then

				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau seluruh barang yang diperlukan sudah kau punya."},
					0
				)
				return
			end

			player:removeItem("titanium_glove", math.random(1, 2))
			player:removeItem("corrupted_blade", math.random(1, 2))
			player:removeItem("electra", math.random(2, 4))

			player.quest["sun_armor"] = 4
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Warrior Sun 4" then
			if not player:karmaCheck("tiger") then
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
					"Kau pikir semudah itu? Pekerjaanmu masih jauh dari selesai. Kesombonganmu terlalu kuat. Ketamakanmu juga, kulihat.",
					"Humble yourself. Slay 200 rabbits."
				},
				1
			)

			if player:killCount("rabbit") < 200 then
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah membunuh 200 kelinci."},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Kudengar batinmu mengeluh atas tugas yang membosankan itu. Mungkin kau belum cukup rendah hati. Kumpulkan 14 gold acorn sambil membunuh 200 tupai."
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

			if player:hasItem("gold_acorn", 14) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau keempat belas gold acorn-nya sudah kau punya."},
					0
				)
				return
			end

			if player:killCount("squirrel") < 200 then
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah membunuh 200 tupai."},
					0
				)
				return
			end

			player:removeItem("gold_acorn", 14)

			player.quest["sun_armor"] = 5
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Warrior Sun 5" then
			if not player:karmaCheck("tiger") then
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
				{t, "Buktikan kepiawaianmu bertempur. Menangkan sedikitnya dua Carnage."},
				1
			)

			if player.registry["carnageWin"] >= 2 then
				player.quest["sun_armor"] = 6
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
			else
				player:dialogSeq(
					{
						t,
						"Dua kemenangan carnage yang disyaratkan belum kau penuhi."
					},
					0
				)
			end
		end

		if choice == "Warrior Sun 6" then
			local item = {}
			local armor = {}

			if player.sex == 0 then
				-- male
				armor = Item("moon_scale_mail")
				item = Item("sun_scale_mail")
			elseif player.sex == 1 then
				-- female
				armor = Item("moon_mail_dress")
				item = Item("sun_mail_dress")
			end

			local armorg = {graphic = item.icon, color = item.iconC}

			if not player:karmaCheck("tiger") then
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
					"Buktikan keunggulanmu atas para monyet. Bunuh kedua pemimpinnya."
				},
				1
			)

			if ((player:killCount("mythic_monkey") >= 1 and player:killCount("monkey_mauler") >= 1) or (player:killCount("divine_monkey") >= 1 and player:killCount("monkey_basher") >= 1) or (player:killCount("spirit_monkey") >= 1 and player:killCount("monkey_avenger") >= 1)) then
				player:dialogSeq(
					{
						t,
						"Sekarang bawakan aku 20.000 keping emas untuk kulebur dan kupakai menempa zirahmu. Bawa juga moon mail-mu dalam keadaan tidak dikenakan."
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

				if player.money < 20000 or player:hasItem(armor.yname, 1) ~= true then
					player:dialogSeq(
						{
							t,
							"Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem(armor.yname, 1)

				player:removeGold(20000)

				local choice2 = player:dialogSeq(
					{
						armorg,
						"Kau ingin mengenakan zirah ini? Ia akan menguras kemampuanmu dan sebagian karmamu."
					},
					1
				)

				if choice2 == true then
					player.baseMight = player.baseMight - 3
					player.baseGrace = player.baseGrace - 2
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

		if speech == "misi" or speech == "kecil" or speech == "misi kecil" then
			MinorQuest.quest(player, npc)
		elseif speech == "selesai" or speech "complete quest" then
			MinorQuest.complete(player, npc)
		end
	end),
}
