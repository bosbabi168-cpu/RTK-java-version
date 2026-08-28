LibrarianNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual", "Bicara dengan Pustakawan"}
		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				LibrarianNpc.buyItems()
			)
		elseif menu == "Jual" then
			player:sellExtend("What are you willing to sell today?", LibrarianNpc.sellItems())
		elseif menu == "Bicara dengan Pustakawan" then
			LibrarianNpc.onSayClick(player, npc)
		end
	end),

	buyItems = function()
		local buyOpts = {
			"legend",
			"divine_chronicles",
			"graced_by_the_muse",
			"the_wandering_monk",
			"tomes_of_the_earth",
			"ranger_code",
			"kwanhonsagje"
		}

		return buyOpts
	end,

	sellItems = function()
		local sellItems = LibrarianNpc.buyItems()

		if (Config.bossDropSalesEnabled) then
			table.insert(sellItems, "key_to_earth")
			table.insert(sellItems, "key_to_fire")
			table.insert(sellItems, "key_to_wind")
			table.insert(sellItems, "key_to_heaven")
			table.insert(sellItems, "key_to_pond")
			table.insert(sellItems, "key_to_thunder")
			table.insert(sellItems, "key_to_water")
			table.insert(sellItems, "key_to_mountain")
		end

		return sellItems
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

		if speech == "ironheart" or speech == "jadespear" then
			Tools.checkKarma(player)

			if player.quest["tutorial_quest"] == 5 then
				player:dialogSeq(
					{
						t,
						"Halo, kulihat kau sudah bertemu kawanku sang Tutor. Semoga ia baik-baik saja belakangan ini.",
						"Ini perpustakaan agung kerajaan; di sini kami menyimpan pengetahuan dari zaman ke zaman.",
						"Salah satu benda berharga yang dicari warga di sini adalah \"Legends\", gulungan yang mengisahkan cerita-cerita besar.",
						"Sayangnya benda itu sangat mahal, tetapi mungkin kalau kau lebih kaya kau bisa memiliki sendiri."
					},
					1
				)

				player:dialogSeq(
					{
						t,
						"... atau lebih baik lagi... ciptakan legendamu sendiri untuk dituliskan dalam gulungan itu!",
						"Ah, betapa banyak impian dan keajaiban. Nah, aku harus kembali bekerja. Sampai jumpa; semoga segera kudengar kisah petualanganmu.",
						"Sebaiknya kau kembali ke tutor sekarang dan terus belajar; banyak sekali yang bisa ia ajarkan."
					},
					1
				)

				player.quest["talked_to_tutor"] = 1
			end
		end

		if speech == "legenda" and npc.mapTitle == "Pond's Library" then
			Tools.checkKarma(player)

			if not player:hasLegend("lost_legend") then
				player:dialogSeq(
					{t, "Aku sungguh tidak paham apa yang kau bicarakan."},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Jadi legendanya sudah kau punya; semoga ia membantumu memenuhi kebutuhanmu.",
					"Apa? Kau tidak bisa membacanya? Coba kulihat...",
					"Aku bisa membacanya dengan jelas; kau buta, ya?",
					"Mungkin pikiranmu belum terbuka untuk melihat melampaui permukaan kertas itu.",
					"Carilah cahaya kehidupan saat ia masih muda, maka kau akan melihat apa yang perlu kau lihat."
				},
				0
			)
		end

		if speech == "koleksi khusus" and npc.mapTitle == "Pond's Library" and player.quest[
			"spy_trials"
		] == 8 then
			local choices = {
				"Para Pengamat Kelinci",
				"Kereta Luncur dan Salju",
				"Night Breeze"
			}
			player:dialogSeq(
				{
					t,
					"** Pond membentangkan beberapa buku beraneka warna dan bentuk **",
					"Ini koleksi khusus kami dari Sanhae."
				},
				0
			)
			if os.time() < player.quest["spy_library_timer"] + 7200 then
				player:dialogSeq(
					{
						t,
						"Maaf, belum ada kabar dari cabang lain. Mungkin kembalilah nanti."
					},
					0
				)
				return
			end
			local choice = player:menuSeq(
				"Keterangan macam apa yang kau cari?",
				choices,
				{}
			)
			if choice == 3 then
				local jewels = {
					graphic = convertGraphic(1588, "item"),
					color = 0
				}
				player.quest["spy_trials"] = 9
				player:dialogSeq(
					{
						t,
						"Ah ya, yang ini sedang digemari, dan kau orang kedua hari ini yang memintanya!",
						"Meski begitu, pakaianmu jauh lebih rapi. Duduklah, akan kuambilkan."
					},
					0
				)
				player:dialogSeq(
					{
						jewels,
						"Kabarnya Imperial Jewels sedang dibawa utusan khusus dari Buya ke Nagnang untuk upacara diplomatik guna membangun kepercayaan antarnegeri.",
						"Kami perlu tahu siapa yang membawanya sekarang dan bagaimana pengangkutannya diatur."
					},
					0
				)
				player:dialogSeq(
					{
						t,
						"Dan tentu saja, benda itu tidak boleh sampai ke istana Nangen.",
						"Sepekan ini kami mengamatinya minum-minum di luar kedai Buya pada malam hari.",
						"Kau butuh cara membawa Hwan ke tempat sepi untuk diinterogasi. Rekan kami pemilik toko ramuan di Buya, Baegil, mestinya bisa memberimu sesuatu.",
						"Tanyakan padanya cara terbaik menyambut Tamu Istimewa kita"
					},
					0
				)
				return
			else
				player.quest["spy_library_timer"] = os.time()
				player:dialogSeq(
					{
						t,
						"Sayangnya yang itu sedang dipinjam. Datanglah lagi kalau kau butuh yang lain..."
					},
					0
				)
			end
		end

		if speech == "koleksi khusus" and npc.mapTitle == "Pond's Library" and player.quest[
			"spy_trials"
		] == 9 then
			local jewels = {graphic = convertGraphic(1588, "item"), color = 0}
			player:dialogSeq(
				{
					t,
					"Ah ya, yang ini sedang digemari, dan kau orang kedua hari ini yang memintanya!",
					"Meski begitu, pakaianmu jauh lebih rapi. Duduklah, akan kuambilkan."
				},
				0
			)
			player:dialogSeq(
				{
					jewels,
					"Kabarnya Imperial Jewels sedang dibawa utusan khusus dari Buya ke Nagnang untuk upacara diplomatik guna membangun kepercayaan antarnegeri.",
					"Kami perlu tahu siapa yang membawanya sekarang dan bagaimana pengangkutannya diatur."
				},
				0
			)
			player:dialogSeq(
				{
					t,
					"Dan tentu saja, benda itu tidak boleh sampai ke istana Nangen.",
					"Sepekan ini kami mengamatinya minum-minum di luar kedai Buya pada malam hari.",
					"Kau butuh cara membawa Hwan ke tempat sepi untuk diinterogasi. Rekan kami pemilik toko ramuan di Buya, Baegil, mestinya bisa memberimu sesuatu.",
					"Tanyakan padanya cara terbaik menyambut Tamu Istimewa kita"
				},
				0
			)
			return
		end

		if speech == "legenda" and npc.mapTitle == "Buya Library" then
			Tools.checkKarma(player)

			if player.quest["wind_armor"] == 0 then
				player:dialogSeq(
					{
						t,
						"Hrmmm... maaf, aku tidak tahu apa yang kau bicarakan."
					},
					0
				)
				return
			end

			player.quest["wind_lake_cavern"] = 1

			player:dialogSeq(
				{
					t,
					"Legenda angin yang hilang? Ya, aku tahu tentang itu, tetapi siapa kau sampai bertanya?",
					"Bisakah kau bayangkan harga pengetahuan semacam itu bagi dirimu dan dunia di sekitarmu?",
					"Tapi... mungkin sudah waktunya. Aku khawatir rahasia itu sudah dicuri dari ruang simpannya oleh orang lain yang akan memakainya untuk memajukan kejahatan.",
					"Aku tidak bisa menceritakan semuanya, sebab perjalanannya sendiri bagian dari kuncinya. Ingatlah untuk belajar sebanyak mungkin dalam perjalanan ini, seperti seharusnya kau lakukan sepanjang hidup.",
					"Yang perlu kau ketahui dariku hanyalah bahwa jauh di dalam gua di bawah perpustakaan ini terdapat jawaban yang kau cari.",
					"Pergilah sekarang, dan jangan biarkan apa pun menghalangi tujuanmu."
				},
				0
			)

			--player:dialogSeq({t,"I am permitting you to pass into the Library Caverns, please take a lantern and be safe."},0)
		end

		if speech == "peta" or speech == "pecahan" or speech == "pecahan peta" then
			if player.quest["instance"] == 4 then
				player:dialogSeq(
					{
						t,
						"Nah, ini dia. Sepertinya peta pegunungan di utara sini.",
						"Mungkin seseorang dengan pengetahuan sejarah yang dalam bisa membantumu."
					},
					1
				)
			end
			if player.quest["instance"] == 3 then
				if player:hasItem("map_fragment", 5) == true then
					player:removeItem("map_fragment", 5)
					player:addItem("combined_map", 1)
					player.quest["instance"] = 4
					player:dialogSeq(
						{
							t,
							"Nah, ini dia. Sepertinya peta pegunungan di utara sini.",
							"Mungkin seseorang dengan pengetahuan sejarah yang dalam bisa membantumu."
						},
						1
					)
				else
					player:dialogSeq(
						{t, "Apakah kau mendapat potongan peta ini lebih banyak?"},
						1
					)
				end
			end
			if player.quest["instance"] == 2 then
				if player:hasItem("purified_water", 1) == true then
					player:removeItem("purified_water", 1)
					player.quest["instance"] = 3
					player:dialogSeq(
						{
							t,
							"Ah ya, ini dia, potongannya makin jelas.",
							"Sepertinya ini bagian dari peta yang jauh lebih besar.",
							"Kumpulkan 4 potongan lagi untukku, dan kita bisa menyusun petanya untuk melihat ke mana arahnya."
						},
						1
					)
				else
					player:dialogSeq(
						{t, "Kembalilah kepadaku kalau kau sudah mendapat purified water."},
						1
					)
				end
			end
			if player:hasItem("map_fragment", 1) == true and player.quest["instance"] == 1 then
				player.quest["instance"] = 2
				player:dialogSeq(
					{
						t,
						"Apa ini? Kau diutus Tetua Zephyr untuk mencari tahu lebih banyak tentang peta ini?",
						"Kelihatannya cukup kotor. Mari kita coba bersihkan",
						"Kumpulkan purified water lalu kembalilah kepadaku. Akan kutangani potongan berharga ini dengan sangat hati-hati."
					},
					1
				)
			end
		end
	end)
}
