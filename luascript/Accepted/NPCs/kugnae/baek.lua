BaekNpc = {
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

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				BaekNpc.buyItems()
			)
		elseif menu == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				BaekNpc.sellItems()
			)
		end
	end),

	buyItems = function()
		local buyItems = {
			"clear_water_song",
			"sacred_poem",
			"war_poem",
			"moon_paper",
			"legend"
		}

		return buyItems
	end,

	sellItems = function()
		local sellItems = BaekNpc.buyItems()

		if (Config.bossDropSalesEnabled) then
			table.insert(sellItems, "chung_ryong_key")
			table.insert(sellItems, "baekho_key")
			table.insert(sellItems, "hyun_moo_key")
			table.insert(sellItems, "ju_jak_key")
		end

		return sellItems
	end,

	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		local baekDialog = Tools.configureDialog(player, npc)

		if speech == "chung ryong" or speech == "baekho" or speech == "ju jak" or speech == "hyun moo" then
			Tools.checkKarma(player)

			if player.level < 99 then
				player:dialogSeq(
					{
						baekDialog,
						"Kau terlalu muda untuk berubah pikiran soal hal semacam ini. Kembalilah nanti dan kita bisa bicara lagi."
					},
					0
				)
				return
			end

			if player.class >= 10 then
				player:dialogSeq(
					{
						baekDialog,
						"Kau tidak bisa bergabung dengan subjalur NPC selagi kau bagian dari subjalur PC."
					},
					0
				)
				return
			end

			if (not player:karmaCheck("ox") and not Config.freeNpcSubpathsEnabled) then
				player:dialogSeq({baekDialog, "Kembalilah kalau karmamu sudah lebih tinggi."}, 0)
				return
			end

			if (player.quest["blessed_by_watcher"] == 0 and not Config.freeNpcSubpathsEnabled) then
				-- not blessed
				player:dialogSeq({baekDialog, "Kau belum diberkati."}, 0)
				return
			end

			if (not player:hasLegend("dog_linguist") and not Config.freeNpcSubpathsEnabled) then
				return
			end

			if player.class > 4 then
				player:dialogSeq(
					{baekDialog, "Kau sudah terikat pada satu subjalur."},
					0
				)
				return
			end

			-- only 4 basic paths

			if speech == "chung ryong" and player.class ~= 1 then
				return
			end
			if speech == "baekho" and player.class ~= 2 then
				return
			end
			if speech == "ju jak" and player.class ~= 3 then
				return
			end
			if speech == "hyun moo" and player.class ~= 4 then
				return
			end

			player:dialogSeq(
				{
					baekDialog,
					"Hmm... ya, aku sudah mempelajari jalan keempat hewan totem. Kau ingin memperoleh pengetahuan dan kekuatan yang hanya diketahui segelintir manusia?",
					"Kau harus berkomitmen melayani hewan-hewan totem. Kalau memilih jalan berat ini, kau TIDAK AKAN PERNAH bisa bergabung dengan subjalur.",
					"Untuk membuktikan pengabdianmu, kau harus banyak berkorban. Pertama, bawakan aku satu Favor dari salah satu hewan Mythic.",
					"Lalu bawakan aku sepuluh kulit dari gua yang paling ditakuti, tempat jalan keluarnya sulit ditemukan dan musuh bersembunyi dalam bayang-bayang.",
					"Terakhir, kau harus mengorbankan 1.000.000.000 pengalaman untuk menuntaskan bagian pelajaran yang paling sulit."
				},
				1
			)

			local favors = {
				"dragons_favor",
				"roosters_favor",
				"dogs_favor",
				"horses_favor",
				"monkeys_favor",
				"oxs_favor",
				"pigs_favor",
				"rabbits_favor",
				"rats_favor",
				"sheeps_favor",
				"snakes_favor",
				"tigers_favor"
			}
			local favorFound = ""

			for i = 1, #favors do
				if player:hasItem(favors[i], 1) == true then
					favorFound = favors[i]
				end
			end

			if (favorFound == "" and not Config.freeNpcSubpathsEnabled) then
				-- no favvor found
				player:dialogSeq(
					{
						baekDialog,
						"Kalau kau bersekutu dengan hewan Mythic, ia akan memberimu Favor."
					},
					0
				)
				return
			end

			if (not Config.freeNpcSubpathsEnabled) then
				player:removeItem(favorFound, 1, 9)
			end

			if (player:hasItem("splendid_tiger_pelt", 10) ~= true and not Config.freeNpcSubpathsEnabled) then
				player:dialogSeq(
					{
						baekDialog,
						"Kau melupakan kulitnya! Sebagai hukuman karena tidak mengikuti petunjukku, sekarang kau juga harus membawakan satu Favor lagi."
					},
					0
				)
				return
			end

			if (not Config.freeNpcSubpathsEnabled) then
				player:removeItem("splendid_tiger_pelt", 10, 9)
			end

			if player.exp < 1000000000 then
				player:dialogSeq(
					{
						baekDialog,
						"Kau pasti melupakan pengalaman yang diperlukan! Sebagai hukuman karena tidak mengikuti petunjukku, sekarang kau harus membawakan satu Favor lagi beserta lebih banyak kulit."
					},
					0
				)
				return
			end

			player.exp = player.exp - 1000000000
			player:sendStatus()

			if not player:hasLegend("attained_totem_mastery") then
				player:addLegend(
					"Attained Totem Mastery (" .. curT() .. ")",
					"attained_totem_mastery",
					3,
					128
				)
			end

			if player.baseClass == 1 then
				player:updatePath(6, player.mark)
			end
			if player.baseClass == 2 then
				player:updatePath(7, player.mark)
			end
			if player.baseClass == 3 then
				player:updatePath(8, player.mark)
			end
			if player.baseClass == 4 then
				player:updatePath(9, player.mark)
			end

			player:calcStat()

			broadcast(
				-1,
				"[SUBPATH]: Congratulations to our newest " .. player.classNameMark .. " " .. player.name .. "!"
			)

			local tbook = {graphic = convertGraphic(20, "item"), color = 0}

			player:dialogSeq(
				{
					tbook,
					"Kau belajar berhari-hari di bawah bimbingan Baek. Kau mempelajari banyak ritual dan legenda."
				},
				1
			)
			player:dialogSeq(
				{
					baekDialog,
					"Kau sudah siap. Sekarang kau bisa mempelajari ritual baru dari Guildmaster-mu. Selamat jalan, kawan!"
				},
				0
			)
		end

		if speech == "kompas" then
			Tools.checkKarma(player)

			player:dialogSeq(
				{
					baekDialog,
					"Halo, kau mencari kompas, ya?",
					"Aku tidak menjual kompas biasa; membosankan sekali.",
					"Sungguh, siapa yang butuh kompas yang cuma menunjuk satu arah?",
					"Semua orang tahu utara itu di mana; ya di utara!",
					"Tidak, tidak... kompasku benar-benar berguna. Ia menunjukkan cara mencapai suatu tempat.",
					"Aku tidak repot dengan tempat-tempat sepele; kalau kau tidak bisa menemukan Buya atau Koguryo, kau memang tidak pantas punya mata.",
					"Pokoknya, kalau ada tempat yang perlu kau tuju, katakan, dan aku bisa membuatkan kompas untuk ke sana."
				},
				0
			)
		end

		if speech == "nagnag" then
			Tools.checkKarma(player)

			if player.quest["baek_compass"] == 0 then
				player.quest["baek_compass"] = 1
				player:dialogSeq(
					{
						baekDialog,
						"Kompas ke istana Nagnag? Belakangan ini banyak yang mencarinya.",
						"Sayangnya permintaannya begitu tinggi sampai bahanku habis.",
						"Kalau kau mau mengambilkan bahan yang kubutuhkan, akan kutunjukkan apa yang harus kau lakukan.",
						"Bawakan aku mangkuk sup untuk menampung airnya.",
						"Sedikit fine metal untuk jarumnya.",
						"Ambil itu, lalu kembalilah kepadaku."
					},
					0
				)
				return
			end

			if player.quest["baek_compass"] == 1 then
				if player:hasItem("soup_bowl", 1) ~= true then
					player:dialogSeq(
						{
							baekDialog,
							"Mana mangkuk supnya? Tanpa itu tidak ada yang bisa kukerjakan."
						},
						0
					)
					return
				end
				if player:hasItem("fine_metal", 1) ~= true then
					player:dialogSeq(
						{
							baekDialog,
							"Aku juga butuh Fine metal; kirimanku belum datang. Kembalilah kalau kau sudah punya."
						},
						0
					)
					return
				end

				player:removeItem("soup_bowl", 1, 9)
				player:removeItem("fine_metal", 1, 9)
				player:addItem("nagnang_compass", 10)
				player:dialogSeq(
					{
						baekDialog,
						"Ahhh, coba kulihat, arahnya tadi apa saja... 192... 284... 82... 76...",
						"Sedikit air untuk mangkuknya, dan selesai! Ini ada beberapa",
						"Semoga berhasil memakainya; bahannya bukan yang terbaik, jadi tiap kompas hanya bisa dipakai sekali.",
						"Kalau kau butuh lagi, bilang saja."
					},
					0
				)

				return
			end
		end
	end)
}
