PoetTrainerNpc = {
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
			table.insert(opts, "Become a Poet")
		elseif player.baseClass == 4 then
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

		if npc.mapTitle == "Staff" and player.baseClass == 4 and player.level >= 10 and not player:hasLegend("destroyed_nagnang_evil") then
			table.insert(opts, "Poet Welcome")
		end

		if player.baseClass == 4 then
			if player.level >= 66 and player:hasLegend("blessed_by_the_stars") and not player:hasLegend("mastered_the_stars") then
				if player.quest["star_armor"] == 0 or player.quest["star_armor"] == 1 then
					table.insert(opts, "Poet Star 1")
				elseif player.quest["star_armor"] == 2 then
					table.insert(opts, "Poet Star 2")
				elseif player.quest["star_armor"] == 3 then
					table.insert(opts, "Poet Star 3")
				end
			end
		end

		if player.baseClass == 4 then
			if player.level >= 76 and player:hasLegend("mastered_the_stars") and not player:hasLegend("understood_the_moon") and not player:hasLegend("survived_the_sun") then
				if player.quest["moon_armor"] == 0 or player.quest["moon_armor"] == 1 then
					table.insert(opts, "Poet Moon 1")
				elseif player.quest["moon_armor"] == 2 then
					table.insert(opts, "Poet Moon 2")
				elseif player.quest["moon_armor"] == 3 then
					table.insert(opts, "Poet Moon 3")
				elseif player.quest["moon_armor"] == 4 then
					table.insert(opts, "Poet Moon 4")
				end
			end
		end

		if player.baseClass == 4 then
			if player.level >= 86 and player:hasLegend("mastered_the_stars") and player:hasLegend("understood_the_moon") and not player:hasLegend("survived_the_sun") then
				if player.quest["sun_armor"] == 0 or player.quest["sun_armor"] == 1 then
					table.insert(opts, "Poet Sun 1")
				elseif player.quest["sun_armor"] == 2 then
					table.insert(opts, "Poet Sun 2")
				elseif player.quest["sun_armor"] == 3 then
					table.insert(opts, "Poet Sun 3")
				elseif player.quest["sun_armor"] == 4 then
					table.insert(opts, "Poet Sun 4")
				elseif player.quest["sun_armor"] == 5 then
					table.insert(opts, "Poet Sun 5")
				elseif player.quest["sun_armor"] == 6 then
					table.insert(opts, "Poet Sun 6")
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
		elseif choice == "Become a Poet" then
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
					"Salam, yang perkasa! Selamat datang di tempat sucianku, tempat sucian sang penyembuh.",
					"Kau datang untuk memilih jalurmu? Kurasa kau akan jadi poet yang hebat, sekaligus pahlawan besar."
				},
				1
			)
			choice2 = player:menuString(
				"Maukah kau menempuh jalur poet?",
				{"Ya", "Ceritakan lebih banyak", "Tidak"}
			)
		elseif choice == "Divine Secret" then
			player:futureSpells(npc)
		elseif choice == "Pelajari Rahasia" then
			player:learnSpell(npc)
		elseif choice == "Forget Secret" then
			player:forgetSpell(npc)
		elseif choice == "Poet Welcome" then
			if player.quest["nangen_acolyte"] == 1 then
				if player:hasItem("forever_branch", 1) ~= true then
					player:dialogSeq(
						{
							t,
							"Aku masih menunggumu membawakan sebatang ranting dari Forever tree."
						},
						0
					)
					return
				end

				player:removeItem("forever_branch", 1)
				player.quest["nangen_acolyte"] = 2

				if not player:hasLegend("nangen_acolyte") then
					player:addLegend(
						"Became Nangen Acolyte (" .. curT() .. ")",
						"nangen_acolyte",
						4,
						128
					)
				end

				player:dialogSeq(
					{
						t,
						"Ah, kayu ini akan sangat berguna bagi ordo Poet ini. Terima kasih."
					},
					0
				)
			end

			if player.quest["nangen_acolyte"] == 2 then
				local sacred_water = {
					graphic = convertGraphic(252, "item"),
					color = 0
				}
				local infected_creature = {
					graphic = convertGraphic(193, "monster"),
					color = 16
				}
				local magic_rabbit = {
					graphic = convertGraphic(125, "monster"),
					color = 25
				}

				if player:killCount("magic_rabbit") ~= 0 then
					player:dialogSeq(
						{
							magic_rabbit,
							"Kau membunuh salah satu kelinci kami! Kau harus menyucikan diri dengan memohon ampun kepada seluruh Hewan Totem."
						},
						0
					)
					return
				end

				if player.quest["destroyed_infected"] == 1 then
					player:dialogSeq(
						{
							t,
							"Selamat! Kau telah membantu kami menjaga keseimbangan kerajaan dengan meredakan tekanan kejahatan yang besar dari dalam tanah kami.",
							"Terimalah jimat pelindung ini. Ia telah diisi sari air suci yang kau pakai untuk memusnahkan kehadiran jahat itu.",
							"Semoga ia melindungimu dari kejahatan dalam pertempuran mendatang. Hanya ini satu-satunya yang akan kuberikan padamu. Sekali lagi terima kasih."
						},
						1
					)

					player:addLegend(
						"Destroyed Nagnang Evil (" .. curT() .. ")",
						"destroyed_nagnang_evil",
						7,
						128
					)
					player:addItem("essence_charm", 1, 0, player.ID)
					player.quest["nangen_acolyte"] = 0
					player.quest["destroyed_infected"] = 0
					player.quest["sacred_water_timer"] = 0
					player.quest["gave_sonhi_pipe"] = 0

					return
				end

				player:dialogSeq(
					{
						t,
						"Sekarang kisah pengabdian kami. Dahulu kala, kehadiran jahat yang besar tumbuh di sini. Ia mulai memengaruhi penduduk kota dan mengubah mereka jadi kaum yang haus perang.",
						"Nagnang selalu tangguh dengan pedang, tetapi orang-orang itu mulai gila kekuasaan. Hanya berkat kehormatan dan kecerdasan Pemimpin kami, Kija, kami berhasil mengusir mereka.",
						"Tetapi kejahatannya tetap ada. Para Poet berhasil mengusir kehadiran itu ke alam lain. Sayangnya ia tumbuh dari perang dan penderitaan, dan tanah kami penuh dengan keduanya.",
						"Untuk menahan kejahatan itu, kami menciptakan kelinci bersihir agar keseimbangannya terjaga. Tetapi kini ia lepas dari kendali kami lagi.",
						"Ia mulai menuangkan seluruh tenaganya ke dalam satu wujud dirinya, jauh di kantong tersembunyi Oblivion. Kalau kekuatannya bertambah terlalu besar, akan robek lubang ke alam ini dan kejahatan itu bebas kembali."
					},
					1
				)

				if os.time() <= player.quest["sacred_water_timer"] then
					player:dialogSeq(
						{
							t,
							"Kau harus menunggu 24 jam sebelum kuberi air suci lagi."
						},
						0
					)
					return
				end

				player.quest["sacred_water_timer"] = os.time() + 86400

				-- 24 hrs
				player:dialogSeq(
					{
						sacred_water,
						"Bawalah air suci ini ke alam itu dan jatuhkan di sebelah makhluk hijau buruk yang terjangkit. Airnya akan memusnahkannya dan keseimbangan pun pulih kembali."
					},
					1
				)
				player:addItem("sacred_water", 1)

				player:flushKills("magic_rabbit")

				player:dialogSeq(
					{
						infected_creature,
						"Ingat! Kau harus berada TEPAT DI SEBELAH makhluk itu dan MENGHADAPNYA agar air sihirnya bekerja! Jangan menghilangkan atau memberikan air ini. Itu tidak sopan."
					},
					1
				)

				player:dialogSeq(
					{
						magic_rabbit,
						"Jangan bunuh satu pun kelinci yang kau lihat. Merekalah sekutu dan kekuatan kami melawan kejahatan."
					},
					1
				)

				player:dialogSeq(
					{
						t,
						"Pintu masuk ke alam itu ada di pagoda tepat di selatan sini. Hanya ordo kami yang boleh masuk. Saat kau kembali... kalau kau kembali... temuilah aku; aku akan sangat berterima kasih atas bantuanmu."
					},
					0
				)
			end

			if player.quest["nangen_acolyte"] == 0 then
				if player.quest["gave_sonhi_pipe"] == 0 then
					if player:hasItem("sonhi_pipe", 1) ~= true then
						player:sendMinitext("Hmmm, apa? Oh, halo, Orang Asing")
						return
					end

					player:removeItem("sonhi_pipe", 1)
					player.quest["gave_sonhi_pipe"] = 1
				end

				player:dialogSeq(
					{
						t,
						"Wah, terima kasih pipanya. Aku belum pernah melihat yang seperti ini sejak kami pindah ke kota. Hadiah yang sungguh berkesan.",
						"Mungkin tidak semua orang asing sejahat yang kami kira. Hmm... mungkin kita bahkan bisa meminta mereka membantu pengabdian kami."
					},
					1
				)

				local choice2 = player:menuSeq(
					"Aku ingin tahu, bersediakah kau membantu kami? Kau harus menempuh jalur Staff dan Nagnang, walau hanya sebentar.",
					{
						"Aku merasa terhormat bisa membantumu dan kotamu yang indah.",
						"Maaf, tetapi jalanku berada di arah lain."
					},
					{}
				)

				if choice2 == 1 then
					-- accept
					player.quest["nangen_acolyte"] = 1
					player:dialogSeq(
						{
							t,
							"Kalau begitu kau tetap harus menjadi pemula Staff sebelum kami mengizinkanmu mengetahui rahasia kami. Kau harus mencari sekeping kayu yang bertahan selamanya.",
							"Bawa kembali kepadaku sebagai hadiah dan kau kuizinkan menjadi pemula Staff. Ingat - HARUS kau sendiri yang memetik ranting itu dari pohonnya."
						},
						0
					)
				elseif choice2 == 2 then
					-- deny
					player:sendMinitext("Kalau begitu semoga jalanmu penuh keberuntungan.")
					player:sendMinitext("Sekali lagi terima kasih pipanya.")
					return
				end
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

			player:addItem("staff_of_defense", 1)

			if player.sex == 0 then
				player:addItem("summer_robes", 1)
				player:addItem("merchant_helm", 1)
			elseif player.sex == 1 then
				player:addItem("summer_gown", 1)
				player:addItem("spring_helmet", 1)
			end

			player:addItem("herb_pipe", 4)

			player:addGold(500)
			player:updatePath(4, 0)
			player:calcStat()

			player:dialogSeq(
				{
					t,
					"Ini zirah dan senjata untukmu. Keduanya khusus jalur pujangga dan akan membantumu memulai.",
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
					"Bercerita soal poet? Poet adalah jalur yang paling dicari, diinginkan setiap jalur lain untuk menemani petualangan.",
					"Poet adalah ahli pertahanan, dengan kemampuan menyembuhkan dan melindungi banyak orang dengan mudah.",
					"Poet berlevel tinggi memperoleh kemampuan memikat binatang, dan bisa menjadi kekuatan luar biasa sendiri kalau keahliannya memadai."
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

				player:addItem("staff_of_defense", 1)

				if player.sex == 0 then
					player:addItem("summer_robes", 1)
				elseif player.sex == 1 then
					player:addItem("summer_gown", 1)
				end

				player:addItem("merchant_helm", 1)
				player:addItem("herb_pipe", 4)

				player:addGold(500)
				player:updatePath(4, 0)
				player:calcStat()

				player:dialogSeq(
					{
						t,
						"Ini zirah dan senjata untukmu. Keduanya khusus jalur pujangga dan akan membantumu memulai.",
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

		if choice == "Poet Star 1" then
			local star = {graphic = convertGraphic(428, "item"), color = 0}
			player.npcGraphic = star.graphic
			player.npcColor = star.color
			player.dialogType = 0
			player.lastClick = npc.ID

			if player.registry["flushed_kills"] == 0 then
				player:flushKills("nine_tailed_fox")
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

			if player:killCount("nine_tailed_fox") >= 9 then
				player.quest["star_armor"] = 2
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)

				return
			end

			player:dialogSeq(
				{
					t,
					"Salah satu yang gagal berpaling pada muslihat dan tipu daya. Temukan dia. Ia menumbuhkan satu ekor untuk tiap keturunan tipu dayanya. Bunuh dia sekali untuk tiap ekornya, lalu kembalilah."
				},
				0
			)
		end

		if choice == "Poet Star 2" then
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
					"Tanganmu belum menunjukkan kekuatan apa pun. Terimalah kekuatan Sen Gloves."
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

			if player:hasItem("sen_glove", 2) ~= true then
				player:dialogSeq(
					{
						t,
						"Sarung tangannya belum ada. Kembalilah kalau sudah kau punya."
					},
					0
				)
				return
			end

			player:removeItem("sen_glove", 2)
			player.quest["star_armor"] = 3
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Poet Star 3" then
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
				item = Item("star_robes")
			elseif player.sex == 1 then
				-- female
				item = Item("star_gown")
			end

			local armor = {graphic = item.icon, color = item.iconC}

			player:dialogSeq(
				{
					t,
					"Untuk berkelip terang, kau harus menunjukkan tombak yang paling terang kelipnya."
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

			if player:hasItem("titanium_lance", 1) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah punya Titanium Lance."},
					0
				)
				return
			end

			player:removeItem("titanium_lance", 1)

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
				player:dialogSeq({t, "Itu milikmu."}, 0)
				player:calcStat()
			end
		end

		if choice == "Poet Moon 1" then
			if player.registry["flushed_kills"] == 0 then
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
				{
					t,
					"Kau menempuh jalur Love. Buktikan pengabdianmu lewat pengorbanan."
				},
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

			player:dialogSeq(
				{
					t,
					"Tidak semua kemenangan lahir dari pertempuran. Bawakan aku 50 mawar untuk dipersembahkan atas nama cinta."
				},
				1
			)

			if player:hasItem("rose", 50) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau 50 mawarnya sudah kau punya."},
					0
				)
				return
			end

			player:removeItem("rose", 50)
			player.quest["moon_armor"] = 2
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Poet Moon 2" then
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
					"Apakah kau memahami rasa kebersamaan sejati? Sudahkah seseorang menyentuh jiwamu?"
				},
				1
			)

			if player.partner == 0 then
				-- no marriage or blood brother/sister
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau sudah mengikat janji."},
					0
				)
				return
			end

			player.quest["moon_armor"] = 3
			player:dialogSeq(
				{
					t,
					"Kulihat kau sudah menemukan pendamping sejatimu. Bagus."
				},
				0
			)
		end

		if choice == "Poet Moon 3" then
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
					"Untuk tugas berikutnya, tunjukkan padaku satu lagi bukti komitmen: membimbing 3 orang."
				},
				1
			)

			if player.registry["mentored"] < 3 then
				player:dialogSeq(
					{
						t,
						"Kembalilah kepadaku kalau kau sudah membimbing sedikitnya 3 orang."
					},
					0
				)
				return
			end

			player.quest["moon_armor"] = 4
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Poet Moon 4" then
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
				armor = Item("star_robes")
				item = Item("moon_robes")
			elseif player.sex == 1 then
				-- female
				armor = Item("star_gown")
				item = Item("moon_gown")
			end

			local armorg = {graphic = item.icon, color = item.iconC}

			player:dialogSeq(
				{
					t,
					"Aku butuh star garment-mu sebagai bahan pakaian barumu."
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
					"Kau ingin mengenakan pakaian ini? Harganya sebagian kemampuanmu dan sebagian karmamu."
				},
				1
			)

			if choice2 == true then
				player.baseWill = player.baseWill - 2
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
				player:dialogSeq({t, "Itu milikmu."}, 0)
				player:calcStat()
			end
		end

		if choice == "Poet Sun 1" then
			if player.registry["flushed_kills"] == 0 then
				player:flushKills("massive_scorpion")
				player:flushKills("sute")
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

			if player:killCount("massive_scorpion") >= 1 and player:killCount("sute") >= 1 then
				player.quest["sun_armor"] = 2
				player.registry["flushed_kills"] = 0
				player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
				return
			end

			player:dialogSeq(
				{
					t,
					"Aku tidak iri padamu, mage. Sebab untuk membuktikan kelayakanmu, kau harus membunuh 1 Massive Scorpion dan Sute."
				},
				1
			)
		end

		if choice == "Poet Sun 2" then
			player:dialogSeq(
				{
					t,
					"Berikutnya bawakan aku: 10 white amber olahan, 1 purified water, dan 6 sen glove"
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

			if player:hasItem("crafted_white_amber", 10) ~= true or player:hasItem(
				"purified_water",
				1
			) ~= true or player:hasItem("sen_glove", 6) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau seluruh barangnya sudah kau punya."},
					0
				)
				return
			end

			player:removeItem("crafted_white_amber", 10)
			player:removeItem("purified_water", 1)
			player:removeItem("sen_glove", 6)

			player.quest["sun_armor"] = 3
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Poet Sun 3" then
			player:dialogSeq(
				{
					t,
					"Berikutnya tunjukkan pengabdianmu kepada keempat hewan totem.",
					"Pertama sembahlah Chung ryong, lalu Baekho, lalu Ju Jak, dan terakhir Hyun moo."
				},
				1
			)

			player:dialogSeq(
				{
					t,
					"Kau tidak perlu kembali kepadaku setiap selesai menyembah satu totem, cukup setelah keempatnya kau sembah."
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

			if player.quest["sun_armor_ntotem"] ~= 4 then
				player:dialogSeq(
					{
						t,
						"Kau belum menyembah keempat totem; kembalilah kepadaku setelah semuanya selesai."
					},
					0
				)
				return
			end

			player.quest["sun_armor"] = 4
			player.quest["sun_armor_ntotem"] = 0

			-- reset registry
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Poet Sun 4" then
			player:dialogSeq(
				{
					t,
					"Berikutnya aku ingin melihat pengabdianmu pada kerajinan. Kau harus mencapai tingkat Adept atau lebih tinggi pada salah satu dari Tailoring, Smithing, atau Carpentry"
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

			if crafting.checkSkillLevel(player, "tailoring", "adept") or crafting.checkSkillLevel(
				player,
				"metalworking",
				"adept"
			) or crafting.checkSkillLevel(player, "woodworking", "adept") then
				player.quest["sun_armor"] = 5
				player:dialogSeq(
					{t, "Kau sudah menunjukkan pengabdianmu pada kerajinan."},
					0
				)
				return
			else
				player:dialogSeq(
					{
						t,
						"Kembalilah kepadaku kalau kau sudah mencapai tingkat Adept pada Tailoring, Smithing, atau Carpentry."
					},
					0
				)
			end
		end

		if choice == "Poet Sun 5" then
			player:dialogSeq({t, "Berikutnya aku minta 2 Titanium Lance"}, 1)

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

			if player:hasItem("titanium_lance", 2) ~= true then
				player:dialogSeq(
					{t, "Kembalilah kalau kau sudah punya 2 titanium lance"},
					0
				)
				return
			end

			player:removeItem("titanium_lance", 2)

			player.quest["sun_armor"] = 6
			player:dialogSeq({t, "Kau sudah melakukannya dengan baik."}, 0)
		end

		if choice == "Poet Sun 6" then
			local item = {}
			local armor = {}
			if player.sex == 0 then
				-- male
				armor = Item("moon_robes")
				item = Item("sun_robes")
			elseif player.sex == 1 then
				-- female
				armor = Item("moon_gown")
				item = Item("sun_gown")
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
				player.baseWill = player.baseWill - 3
				player:removeKarma(5)
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

		if speech == "misi" or speech == "kecil" or speech == "misi kecil" then
			MinorQuest.quest(player, npc)
		elseif speech == "selesai" or speech "complete quest" then
			MinorQuest.complete(player, npc)
		end
	end)
}
