RogueTrainerNpc = {
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
			table.insert(opts, "Become a Rogue")
		elseif player.baseClass == 2 then
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

		if (npc.mapTitle == "Dagger" and player.baseClass == 2 and player.quest["dagger_blue_rooster"] ~= 0 and not player:hasLegend("dagger_guild_member")) then
			table.insert(opts, "Blue Rooster")
		end

		if npc.mapTitle == "Dagger" and player.level >= 10 and player.baseClass == 2 and not player:hasLegend("dagger_guild_member") then
			table.insert(opts, "Dagger Strangers")
		end

		if player.baseClass == 2 then
			if player.level >= 66 and player:hasLegend("blessed_by_the_stars") and not player:hasLegend("mastered_the_stars") then
				if player.quest["star_armor"] == 0 or player.quest["star_armor"] == 1 then
					table.insert(opts, "Rogue Star 1")
				elseif player.quest["star_armor"] == 2 then
					table.insert(opts, "Rogue Star 2")
				elseif player.quest["star_armor"] == 3 then
					table.insert(opts, "Rogue Star 3")
				end
			end
		end

		if player.baseClass == 2 then
			if player.level >= 76 and player:hasLegend("mastered_the_stars") and not player:hasLegend("understood_the_moon") and not player:hasLegend("survived_the_sun") then
				if player.quest["moon_armor"] == 0 or player.quest["moon_armor"] == 1 then
					table.insert(opts, "Rogue Moon 1")
				elseif player.quest["moon_armor"] == 2 then
					table.insert(opts, "Rogue Moon 2")
				elseif player.quest["moon_armor"] == 3 then
					table.insert(opts, "Rogue Moon 3")
				elseif player.quest["moon_armor"] == 4 then
					table.insert(opts, "Rogue Moon 4")
				end
			end
		end

		if player.baseClass == 2 then
			if player.level >= 86 and player:hasLegend("mastered_the_stars") and player:hasLegend("understood_the_moon") and not player:hasLegend("survived_the_sun") or player.gmLevel > 0 then
				if player.quest["sun_armor"] == 0 or player.quest["sun_armor"] == 1 then
					table.insert(opts, "Rogue Sun 1")
				elseif player.quest["sun_armor"] == 2 then
					table.insert(opts, "Rogue Sun 2")
				elseif player.quest["sun_armor"] == 3 then
					table.insert(opts, "Rogue Sun 3")
				elseif player.quest["sun_armor"] == 4 then
					table.insert(opts, "Rogue Sun 4")
				elseif player.quest["sun_armor"] == 5 then
					table.insert(opts, "Rogue Sun 5")
				end
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
		elseif choice == "Become a Rogue" then
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
					"Salam, yang perkasa! Selamat datang di tempat sucianku, tempat sucian petarung paling mematikan!",
					"Kau datang untuk memilih jalurmu? Kurasa kau akan jadi rogue yang hebat, sekaligus pahlawan besar."
				},
				1
			)
			choice2 = player:menuString(
				"Maukah kau menempuh jalur rogue?",
				{"Ya", "Ceritakan lebih banyak", "Tidak"}
			)
		elseif choice == "Divine Secret" then
			if npc.mapTitle == "Dagger" and player.alignment == 0 then
				player:futureSpells(npc, {"daggers_remedy_rogue"})
			elseif npc.mapTitle == "Dagger" and player.alignment == 1 then
				player:futureSpells(npc, {"kwisin_daggers_remedy_rogue"})
			elseif npc.mapTitle == "Dagger" and player.alignment == 2 then
				player:futureSpells(npc, {"mingken_daggers_remedy_rogue"})
			elseif npc.mapTitle == "Dagger" and player.alignment == 3 then
				player:futureSpells(npc, {"ohaeng_daggers_remedy_rogue"})
			else
				player:futureSpells(npc)
			end
		elseif choice == "Pelajari Rahasia" then
			if npc.mapTitle == "Dagger" and player.mark >= 2 and player.alignment == 0 then
				player:learnSpell(npc, {"daggers_remedy_rogue"})
			elseif npc.mapTitle == "Dagger" and player.mark >= 2 and player.alignment == 1 then
				player:learnSpell(npc, {"kwisin_daggers_remedy_rogue"})

				-- was KWI-Sin Dagger (but this doesn't happen
			elseif npc.mapTitle == "Dagger" and player.mark >= 2 and player.alignment == 2 then
				player:learnSpell(npc, {"mingken_daggers_remedy_rogue"})

				-- was Ming-Ken Dagger (but this doesn't happen
			elseif npc.mapTitle == "Dagger" and player.mark >= 2 and player.alignment == 3 then
				player:learnSpell(npc, {"ohaeng_daggers_remedy_rogue"})

				-- was Ohaeng (but this doesn't happen
			else
				player:learnSpell(npc)
			end
		elseif choice == "Forget Secret" then
			player:forgetSpell(npc)
		elseif choice == "Dagger Strangers" then
			if player:hasLegend("dagger_guild_member") then
				return
			end

			if player.quest["dagger_blue_rooster"] ~= 0 then
				return
			end

			if player.quest["dagger_clicked"] == 0 then
				player:sendMinitext("Aku tidak akan pernah berbicara denganmu.")
				player.quest["dagger_clicked"] = 1
				return
			end

			if player.quest["dagger_clicked"] == 1 then
				player:sendMinitext("Ganggu aku lagi, dan kau akan mati sambil melihat apa yang bersembunyi dalam bayang-bayang.")
				player.quest["dagger_clicked"] = 2
				return
			end

			if player.quest["dagger_clicked"] == 2 then
				player:sendMinitext("Ini balasan atas kelakuanmu yang menyebalkan. Serang!")
				player.quest["dagger_clicked"] = 3

				-- spawn 3 mobs that look just like npc, despawn after 5 seconds if player goes out of range
				npc:spawn("dagger_assassin", npc.x - 1, npc.y, 1)
				npc:spawn("dagger_assassin", npc.x + 1, npc.y, 1)
				npc:spawn("dagger_assassin", npc.x, npc.y + 1, 1)
				return
			end

			if player.quest["dagger_clicked"] == 3 then
				player.quest["dagger_blue_rooster"] = 1
				player:dialogSeq(
					{
						t,
						"Jadi kau tetap kembali kepadaku bahkan setelah serangan itu. Ada secercah harapan padamu... atau kebodohan. Kembalilah kalau kau melihat Blue Rooster."
					},
					0
				)
				return
			end
		elseif choice == "Blue Rooster" then
			if player.quest["dagger_blue_rooster"] == 3 then
				if player.quest["handed_maso_scroll"] == 0 then
					player:dialogSeq(
						{
							t,
							"Aku masih menunggumu menuntaskan tugas terakhirmu."
						},
						0
					)
					return
				end

				player:addItem("round_buckler", 1, 0, player.ID)
				player:addLegend(
					"Anggota guild Dagger (" .. curT() .. ")",
					"dagger_guild_member",
					9,
					128
				)
				player.quest["dagger_clicked"] = 0
				player.quest["dagger_blue_rooster"] = 0
				player.quest["crow_took_silvery_acorn"] = 0
				player.quest["crow_took_silvery_acorn2"] = 0
				player.quest["handed_maso_scroll"] = 0
				player.quest["seen_blue_rooster"] = 0

				player:dialogSeq(
					{
						t,
						"Bagus sekali! Kau membuktikan diri layak mendapat perlindungan para Dagger.",
						"Ambil perisai ini. Ia melindungi tanpa mengurangi kegesitan atau kesenyapanmu.",
						"Semoga ia membantumu dalam tugas-tugas mendatang. Hanya ini satu-satunya yang akan kuberikan padamu."
					},
					1
				)
				return
			end

			if player.quest["dagger_blue_rooster"] == 2 then
				if player:hasItem("silvered_acorn", 1) ~= true then
					player:dialogSeq(
						{
							t,
							"Pertama, temui si pura-pura Maro di Kugnae.",
							"Ia menyimpan Silver acorn di sakunya sebagai jimat keberuntungan. Rebut untukku, sebagai bukti bahwa kepiawaianmu pun melampaui dia."
						},
						0
					)
					return
				end

				player:removeItem("silvered_acorn", 1)
				player:addItem("maso_scroll", 1)

				player.quest["dagger_blue_rooster"] = 3
				player:dialogSeq(
					{
						t,
						"Jadi kau berhasil merebut acorn dari si tolol Maro itu? Bagus. Sekarang saatnya kau mengelabui Maso di Buya.",
						"Acorn itu kuambil dan kusimpan di tempat aman. Bawa gulungan ini dan selipkan ke saku Maso. Jadi pencopet itu mudah; menyelipkan barang ke saku orang sedikit lebih sulit.",
						"Kalau itu selesai dan kau kembali kepadaku dengan kemampuan mempelajari mantra, kau layak mengenakan seragam para Dagger."
					},
					0
				)
				return
			end

			if player.quest["seen_blue_rooster"] == 0 then
				player:dialogSeq(
					{t, "Temui aku lagi kalau kau melihat Blue Rooster."},
					0
				)
				return
			end

			player.quest["dagger_blue_rooster"] = 2
			player:dialogSeq(
				{
					t,
					"Ah, sudah melihat Blue Rooster rupanya? Bagus kau datang atas panggilanku.",
					"Aku memutuskan bahwa banyak dari kalian Orang Asing bisa jadi tambahan yang baik bagi klan kecilku. Mungkin jalan Malam tidak akan hilang.",
					"Tapi pertama-tama aku perlu tahu apakah kau punya bakatnya. Para yang mengaku guru Rogue itu cuma pura-pura. Pertama, temui si pura-pura Maro di Kugnae.",
					"Ia menyimpan Silver acorn di sakunya sebagai jimat keberuntungan. Rebut untukku, sebagai bukti bahwa kepiawaianmu pun melampaui dia."
				},
				0
			)
		end

		if choice2 == "Ya" then
			player:dialogSeq(
				{
					t,
					"Bagus! Itu keputusan yang tepat. Aku melihatmu kelak jadi pahlawan besar di tanah ini. Sekarang biar kubekali kau dengan perlengkapan."
				},
				1
			)

			player:addItem("swift_dagger", 1)
			player:addItem("bears_liver", 26)
			if player.sex == 0 then
				player:addItem("merchant_waistcoat", 1)
				player:addItem("merchant_helm", 1)
			elseif player.sex == 1 then
				player:addItem("summer_blouse", 1)
				player:addItem("spring_helmet", 1)
			end

			player:addGold(500)
			player:updatePath(2, 0)
			player:calcStat()

			player:dialogSeq(
				{
					t,
					"Ini zirah dan senjata untukmu. Keduanya khusus jalur rogue dan akan membantumu memulai.",
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
					"Bercerita soal rogue? Mereka yang paling mematikan di antara kelas petarung. Lincah, gesit, cepat, dan tak tertandingi dalam duel; pembunuh sejati.",
					"Rogue memakai sedikit sihir dalam pertempuran dan banyak keahlian untuk menyerang musuh. Kami menyerang satu per satu, tetapi membunuh cepat dan efisien, bergerak terlalu gesit untuk mudah dikenai.",
					"Kami bisa menghadapi makhluk tunggal seorang diri dengan cakap; untuk pertempuran besar kami butuh sedikit bantuan penyembuh."
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

				player:addItem("swift_dagger", 1)
				player:addItem("bears_liver", 26)
				if player.sex == 0 then
					player:addItem("summer_armor", 1)
				elseif player.sex == 1 then
					player:addItem("summer_armor_dress", 1)
				end

				player:addItem("spring_helmet", 1)

				player:addGold(500)
				player:updatePath(2, 0)
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

		if choice == "Rogue Star 1" then
			local star = {graphic = convertGraphic(428, "item"), color = 0}
			player.npcGraphic = star.graphic
			player.npcColor = star.color
			player.dialogType = 0
			player.lastClick = npc.ID

			if player.registry["flushed_kills"] == 0 then
				player:flushKills("muck_ogre")
				player:flushKills("slime_ogre")
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

			if player:killCount("slime_ogre") >= 2 or player:killCount("muck_ogre") >= 2 then
				player.quest["star_armor"] = 2
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)

				return
			end

			player:dialogSeq(
				{
					t,
					"Di antara yang gagal ada para ogre. Bahkan yang paling gesit pun kekurangan cahaya. Bunuh 2 Slime Ogre atau 2 Muck Ogre, lalu kembalilah."
				},
				0
			)
			return
		end

		if choice == "Rogue Star 2" then
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
					"Kau sudah membuktikan kekuatanmu, tetapi bagaimana kelenturanmu? Bawakan aku dua silent band."
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

			if player:hasItem("whisper_bracelet", 2) ~= true then
				player:dialogSeq(
					{
						t,
						"Gelangnya belum ada. Kembalilah kalau sudah kau punya."
					},
					0
				)
				return
			end

			player:removeItem("whisper_bracelet", 2)
			player.quest["star_armor"] = 3
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Rogue Star 3" then
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
				item = Item("star_waistcoat")
			elseif player.sex == 1 then
				-- female
				item = Item("star_blouse")
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

			if player:hasItem("steelthorn", 1) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah punya Steelthorn."},
					0
				)
				return
			end

			player:removeItem("steelthorn", 1)

			local choice2 = player:dialogSeq(
				{
					armor,
					"Kau ingin mengenakan zirah ini? Harganya sebagian kemampuanmu dan sebagian karmamu."
				},
				1
			)

			if choice2 == true then
				player.baseGrace = player.baseGrace - 1
				player.karma = player.karma - 1
				player:addItem(item.yname, 1, 0, player.ID)
				player.quest["star_armor"] = 0
				player.quest["flushed_kills"] = 0
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

		if choice == "Rogue Moon 1" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("dog_assassin")

				-- dog 1
				player:flushKills("dog_cutthroat")

				-- dog 2
				player:flushKills("dog_avenger")

				-- dog 3
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
				{t, "Kau menempuh jalur Riches. Buktikan kelayakanmu."},
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

			if player:killCount("dog_assassin") >= 1 or player:killCount("dog_cutthroat") >= 1 or player:killCount("dog_avenger") >= 1 then
				player.quest["moon_armor"] = 2
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

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

			player:dialogSeq(
				{
					t,
					"Anjing-anjing di kota mengajarkan rahasia kepada banyak orang. Tetapi kebanyakan binatang tidak sesuci itu. Bunuh Anjing yang menodai mawar indah dengan menggigitnya di mulut."
				},
				1
			)
		end

		if choice == "Rogue Moon 2" then
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

			player:dialogSeq(
				{
					t,
					"Kau menempuh jalur Riches. Buktikan kelayakanmu. Bawakan aku semua berikut ini SEKALIGUS."
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Bulan purnama menetes ke bumi, meresap ke dalam tanah. Bawakan lima puluh bulatan sweet amber.",
					"Bulan gelap menetes lebih dalam lagi ke bumi. Bawakan sepuluh amber yang lebih gelap ini.",
					"Hanya yang paling senyap bisa mengenakan busana ini. Bawakan dua whisper bracelet untuk menyenyapkan tanganmu.",
					"Bawakan aku dua steelthorn untuk memotong bahan bulan.",
					"Bulan berpihak pada yang beruntung. Bawakan juga satu lucky coin."
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

			--player:dialogSeq({t,"Bring me 2 Whisper Bracelets, 2 Steelthorns, 50 Ambers, 10 Dark Ambers, 1 Lucky Coin, and 15,000 coins."},1)

			if player:hasItem("whisper_bracelet", 2) ~= true or player:hasItem(
				"steelthorn",
				2
			) ~= true or player:hasItem("amber", 50) ~= true or player:hasItem(
				"dark_amber",
				10
			) ~= true or player:hasItem("lucky_coin", 1) ~= true or player.money < 15000 then
				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			player:removeItem("whisper_bracelet", 2)
			player:removeItem("steelthorn", 2)
			player:removeItem("amber", 50)
			player:removeItem("dark_amber", 10)
			player:removeItem("lucky_coin", 1)
			player.money = player.money - 15000
			player:sendStatus()

			player.quest["moon_armor"] = 3
			player.registry["flushed_kills"] = 0
			player:dialogSeq({t, "Kau sudah membuktikan kelayakan bendawimu."}, 0)
		end

		if choice == "Rogue Moon 3" then
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

			player:dialogSeq(
				{
					t,
					"Sabar, kawan rogue-ku yang tidak sabaran. Sekarang perlihatkan White Moon Axe-mu. Akan kuikat ia pada jiwamu, supaya tak seorang pun bisa memakainya."
				},
				1
			)

			if player:hasItem("white_moon_axe", 1) ~= true then
				player:dialogSeq({t, "Kembalilah kalau kapaknya sudah kau punya."}, 0)
				return
			end

			player:removeItem("white_moon_axe", 1)
			player:addItem("white_moon_axe", 1, 0, player.ID)
			player.quest["moon_armor"] = 4
			player.registry["flushed_kills"] = 0
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
			return
		end

		if choice == "Rogue Moon 4" then
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
				armor = Item("star_waistcoat")
				item = Item("moon_waistcoat")
			elseif player.sex == 1 then
				-- female
				armor = Item("star_blouse")
				item = Item("moon_blouse")
			end

			local armorg = {graphic = item.icon, color = item.iconC}

			player:dialogSeq(
				{
					t,
					"Kekuatan bulan tidak semudah itu ditundukkan! Bawakan aku star blouse-mu."
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
				player.baseGrace = player.baseGrace - 2
				player.karma = player.karma - 2
				player:addItem(item.yname, 1, 0, player.ID)
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

		if choice == "Rogue Sun 1" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("ice_panther")

				-- ogres
				player:flushKills("ogre_citelam")

				-- ogres
				player:flushKills("squirrel")

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

			if not player:karmaCheck("bear") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player:killCount("ice_panther") >= 12 and player:killCount("ogre_citelam") >= 1 then
				player.quest["sun_armor"] = 2
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			elseif player:killCount("ice_panther") < 12 then
				player:dialogSeq(
					{
						t,
						"Ice panther yang kau bunuh belum cukup. Kau harus membunuh 12 ekor lalu kembali kepadaku."
					},
					0
				)
				return
			elseif player:killCount("ogre_citelam") < 1 then
				player:dialogSeq(
					{t, "Aku masih menunggumu membunuh Citelam."},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Aku tidak iri padamu, rogue. Sebab untuk membuktikan kelayakanmu, kau harus membunuh selusin ice panther dan Citelam yang ditakuti! Kalau kau selamat, kembalilah supaya kita bisa melanjutkan."
				},
				1
			)
		end

		if choice == "Rogue Sun 2" then
			local normalRatMobs = {
				"mythic_mouse",
				"vile_rat",
				"blood_rat",
				"rat_sentry",
				"divine_mouse",
				"mud_rat",
				"hunter_rat",
				"lava_rat",
				"rat_guardian",
				"spirit_mouse",
				"earth_rat",
				"fire_rat",
				"beady_eyed_stalker",
				"rat_defender"
			}

			if player.registry["flushed_kills"] == 0 then
				for i = 1, #normalRatMobs do
					player:flushKills(normalRatMobs[i])
				end

				player:flushKills("mythic_rat")

				-- rat 1 bosses
				player:flushKills("mighty_mouse")
				player:flushKills("divine_rat")

				-- rat 2 bosses
				player:flushKills("rat_lord")
				player:flushKills("spirit_rat")

				-- rat 3 bosses
				player:flushKills("rat_avenger")
				player.registry["flushed_kills"] = 1
			end

			player:dialogSeq(
				{
					t,
					"Kau sudah membuktikan kepiawaianmu bertarung jarak dekat. Tetapi menjadi rogue jauh lebih dari itu, bukan?"
				},
				1
			)
			player:dialogSeq(
				{
					t,
					"Sekarang buktikan keahlian dan kesenyapanmu. Bunuh kedua pemimpin tikus itu, TANPA membunuh makhluk lain di gua tikus."
				},
				1
			)

			if not player:karmaCheck("bear") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			for i = 1, #normalRatMobs do
				-- scans the basic mobs in all mythic rat cave levels, looking for any kills
				if player:killCount(normalRatMobs[i]) >= 1 then
					player.registry["flushed_kills"] = 0
					player:dialogSeq(
						{
							t,
							"Kau membunuh binatang yang seharusnya tidak kau sentuh. Coba lagi."
						},
						0
					)
					return
				end
			end

			if ((player:killCount("mythic_rat") >= 1 and player:killCount("mighty_mouse") >= 1) or (player:killCount("divine_rat") >= 1 and player:killCount("rat_lord") >= 1) or (player:killCount("spirit_rat") >= 1 and player:killCount("rat_avenger") >= 1)) then
				player.quest["sun_armor"] = 3
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end
		end

		if choice == "Rogue Sun 3" then
			local caveLevel = player:mythicCaveReqCheck("rabbit")
			local mobboss1 = ""
			local mobboss2 = ""
			local normalRabbitMobs = {
				"golden_hare",
				"mad_rabbit",
				"giant_hare",
				"rabbit_sentry",
				"golden_rabbit",
				"mad_hare",
				"giant_rabbit",
				"rabbit_guardian",
				"hop",
				"thump",
				"fluff",
				"rabbit_defender"
			}

			if caveLevel == 1 then
				mobboss1 = "Mythic hare"
				mobboss2 = "Hare witch"
			elseif caveLevel == 2 then
				mobboss1 = "Divine rabbit"
				mobboss2 = "Rabbit witch"
			elseif caveLevel == 3 then
				mobboss1 = "Spirit rabbit"
				mobboss2 = "Rabbit avenger"
			end

			if player.registry["flushed_kills"] == 0 then
				for i = 1, #normalRabbitMobs do
					player:flushKills(normalRabbitMobs[i])
				end

				player:flushKills("mythic_hare")

				-- rabbit 1 bosses
				player:flushKills("hare_witch")
				player:flushKills("divine_rabbit")

				-- rabbit 2 bosses
				player:flushKills("rabbit_witch")
				player:flushKills("spirit_rabbit")

				-- rabbit 3 bosses
				player:flushKills("rabbit_avenger")
				player.registry["flushed_kills"] = 1
			end

			player:dialogSeq(
				{
					t,
					"Ah, tetapi mengalahkan tikus yang kikuk dengan kesenyapan itu mudah! Sekarang tantangan yang sesungguhnya."
				},
				1
			)

			player:dialogSeq(
				{
					t,
					"Bunuh si licik " .. mobboss1 .. " dan " .. mobboss2 .. ", TANPA membunuh makhluk lain di gua itu."
				},
				1
			)

			if not player:karmaCheck("bear") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			for i = 1, #normalRabbitMobs do
				-- scans the basic mobs in all mythic rat cave levels, looking for any kills
				if player:killCount(normalRabbitMobs[i]) >= 1 then
					player:flushKills(normalRabbitMobs[i])
					player.registry["flushed_kills"] = 0
					player:dialogSeq(
						{
							t,
							"Kau membunuh binatang yang seharusnya tidak kau sentuh. Coba lagi."
						},
						0
					)
					return
				end
			end

			if ((player:killCount("mythic_hare") >= 1 and player:killCount("hare_witch") >= 1) or (player:killCount("divine_rabbit") >= 1 and player:killCount("rabbit_witch") >= 1) or (player:killCount("spirit_rabbit") >= 1 and player:killCount("rabbit_avenger") >= 1)) then
				player.quest["sun_armor"] = 4
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end
		end

		if choice == "Rogue Sun 4" then
			player:dialogSeq(
				{
					t,
					"Kesenyapanmu mengesankan! Tetapi Jalurmu adalah Riches, bukan kesenyapan. Bawakan aku 50.000 keping emas, delapan steelthorn, lima whisper bracelet, dan enam corrupted ring."
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

			if not player:karmaCheck("bear") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player.money < 50000 then
				player:dialogSeq({t, "Kembalilah kalau emasmu sudah cukup."}, 0)
				return
			end

			if player:hasItem("steelthorn", 8) ~= true or player:hasItem(
				"whisper_bracelet",
				5
			) ~= true or player:hasItem("corrupted_ring", 6) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			player:removeGold(50000)
			player:removeItem("steelthorn", math.random(1, 4))
			player:removeItem("whisper_bracelet", math.random(1, 2))
			player:removeItem("corrupted_ring", math.random(1, 3))

			player.quest["sun_armor"] = 5
			player:dialogSeq(
				{t, "Kerjamu bagus. Sebagian besar barangnya boleh kau simpan."},
				0
			)
		end

		if choice == "Rogue Sun 5" then
			local item = {}
			local armor = {}
			if player.sex == 0 then
				-- male
				armor = Item("moon_waistcoat")
				item = Item("sun_waistcoat")
			elseif player.sex == 1 then
				-- female
				armor = Item("moon_blouse")
				item = Item("sun_blouse")
			end

			local armorg = {graphic = item.icon, color = item.iconC}

			player:dialogSeq(
				{
					t,
					"Kau pikir semudah itu? Pekerjaanmu masih jauh dari selesai. Tetapi kesombonganmu terlalu kuat. Ketamakanmu juga. Aku mengerti."
				},
				1
			)

			player:dialogSeq(
				{
					t,
					"Rendahkan hatimu. Kumpulkan 20 gold acorn sambil membunuh 200 tupai. Saat kembali, bawa moon garment-mu dalam keadaan tidak dikenakan."
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

			if not player:karmaCheck("bear") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor untuk bertahan di bawah matahari. Perbaiki karmamu lalu kembalilah."
					},
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

			if player:hasItem(armor.yname, 1) ~= true or player:hasItem("gold_acorn", 20) ~= true then
				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			player:removeItem(armor.yname, 1)
			player:removeItem("gold_acorn", 20)

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
				player.baseGrace = player.baseGrace - 3
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

		if speech == "congkel kunci" then
			if npc.mapTitle == "Maso Sanctum" or npc.mapTitle == "Kwi-Sin Maso" or npc.mapTitle == "Ming-Ken Maso" or npc.mapTitle == "Ohaeng Maso" then
				Tools.checkKarma(player)

				if player.quest["maso_lockpick"] == 0 then
					return
				end

				if player.quest["maso_lockpick"] == 1 then
					player:dialogSeq(
						{
							t,
							"Jadi kau butuh pencongkel kunci? Siapa yang bilang aku bisa membuatnya?",
							"Tidak penting, akan kucari tahu sendiri. Jadi, kau butuh satu, ya?",
							"Biasanya benda ini kusimpan untuk orang istimewa saja, tetapi karena kau sudah tahu, kurasa tidak apa-apa.",
							"Bukannya kau punya keahlian yang diperlukan; membuka pintu biasanya butuh lebih dari sekadar \"goyangan\".",
							"Nah, coba lihat, aku butuh sepotong Wood untuk itu, dan Fine steel dagger untuk mengukirnya."
						},
						0
					)

					if player:hasItem("wood_scraps", 1) ~= true or player:hasItem(
						"fine_steel_dagger",
						1
					) ~= true then
						player:dialogSeq(
							{
								t,
								"Aku bisa membuatkan pencongkel kunci begitu barang yang diperlukan ada padamu: satu wood scrap dan satu fine steel dagger."
							},
							0
						)
						return
					end

					player:removeItem("wood_scraps", 1)
					player:removeItem("fine_steel_dagger", 1)
					player:addItem("lockpick", 1)

					player:dialogSeq(
						{
							t,
							"Ini dia, semoga berhasil memakainya... dan jangan sampai patah, benda ini rapuh sekali."
						},
						0
					)
				end
			end
		elseif speech == "misi" or speech == "kecil" or speech == "misi kecil" then
			MinorQuest.quest(player, npc)
		elseif speech == "selesai" or speech "complete quest" then
			MinorQuest.complete(player, npc)
		end
	end),

	handItem = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local invItem = player:getInventoryItem(player.invSlot)

		if npc.mapTitle == "Maso Sanctum" or npc.mapTitle == "Kwi-Sin Maso" or npc.mapTitle == "Ming-Ken Maso" or npc.mapTitle == "Ohaeng Maso" then
			if invItem.yname == "maso_scroll" and player.quest["dagger_blue_rooster"] == 3 then
				player:removeItem("maso_scroll", 1)
				player.quest["handed_maso_scroll"] = 1
				player:dialogSeq(
					{
						t,
						"\".....Eh? Apa ini?....\"",
						"\"Si tolol sombong itu! Apa Maro sungguh mengira bisa menghancurkanku?!!! Aku harus mengambil tindakan terhadapnya...\""
					},
					0
				)
			end
		end
	end)
}
