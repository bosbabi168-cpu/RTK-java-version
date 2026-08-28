ParkNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.baseClass ~= 2 then
			player:dialogSeq({t, "Maaf, aku tidak bisa menolong kaummu."}, 0)
			return
		end

		if not player:hasLegend("dagger_guild_member") then
			player:dialogSeq(
				{t, "Kau harus menyelesaikan tugas Nagnang jalurmu dulu."},
				0
			)
			return
		end

		player:dialogSeq(
			{
				t,
				"Syukurlah kau datang! Kuharap kau mengusir pasukan jahat itu dari sini. Antek-antek Nagnag menculikku dari rumahku dan memaksaku bekerja untuk mereka di tempat ini.",
				"Aku diperbudak oleh mantra jahat yang mengikatku di sini. Aku tidak akan pernah bisa kabur. Mereka memaksaku membuat zirah dan senjata khusus untuk misi yang sedang mereka jalankan,",
				"Aku ingin kau membalaskan dendamku pada Nagnag atas apa yang ia lakukan padaku. Satu-satunya yang bisa kubantu adalah membuatkanmu barang-barang yang dulu kubuat untuk para rogues-nya."
			},
			1
		)

		local items = {}

		if player.level >= 11 then
			table.insert(items, "Battle amulet")
		end
		if player.level >= 39 then
			table.insert(items, "Battle rune")
		end
		if player.level >= 69 then
			table.insert(items, "Light buckler")
		end
		if player.level >= 97 then
			table.insert(items, "Basic buckler")
		end
		if player.level >= 99 and player.mark >= 1 then
			table.insert(items, "Heavy buckler")
		end
		if player.level >= 99 and player.mark >= 2 then
			table.insert(items, "Amber buckler")
		end
		if player.level >= 99 and player.mark >= 3 then
			table.insert(items, "Enchanted buckler")
		end
		if player.level >= 99 and player.mark >= 4 then
			table.insert(items, "Mystic buckler")
		end

		local choice = player:menuString(
			"Apa yang ingin kubantu buatkan?",
			items
		)

		if choice == "Battle amulet" then
			player:dialogSeq(
				{
					t,
					"Battle amulet adalah amulet yang luar biasa, sangat berkuasa.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Earth armor, 10 bear's liver, satu herb pipe, dan 1,000 emas."
				},
				1
			)

			if player:hasItem("earth_armor", 1) ~= true or player:hasItem("bears_liver", 10) ~= true or player:hasItem(
				"herb_pipe",
				1
			) ~= true or player.money < 1000 then
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			local choice2 = player:menuSeq(
				"Kulihat semuanya sudah kau bawa. Mau kubuatkan sekarang?",
				{"Ya", "Tidak"},
				{}
			)

			if choice2 == 1 then
				-- yes

				if player:hasItem("earth_armor", 1) ~= true or player:hasItem("bears_liver", 10) ~= true or player:hasItem(
					"herb_pipe",
					1
				) ~= true or player.money < 1000 then
					player:dialogSeq(
						{
							t,
							"Kembalilah kepadaku kalau barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("earth_armor", 1, 9)
				player:removeItem("bears_liver", 10, 9)
				player:removeItem("herb_pipe", 1, 9)
				player:removeGold(1000)

				player:addItem("battle_amulet", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Battle amulet milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
					0
				)
			end
		elseif choice == "Battle rune" then
			player:dialogSeq(
				{
					t,
					"Battle rune adalah rune yang luar biasa, sangat berkuasa.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Moonblade, satu Fine metal, dan 4,000 emas."
				},
				1
			)

			if player:hasItem("moonblade", 1) ~= true or player:hasItem("fine_metal", 1) ~= true or player.money < 4000 then
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			local choice2 = player:menuSeq(
				"Kulihat semuanya sudah kau bawa. Mau kubuatkan sekarang?",
				{"Ya", "Tidak"},
				{}
			)

			if choice2 == 1 then
				-- yes

				if player:hasItem("moonblade", 1) ~= true or player:hasItem("fine_metal", 1) ~= true or player.money < 4000 then
					player:dialogSeq(
						{
							t,
							"Kembalilah kepadaku kalau barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("moonblade", 1, 9)
				player:removeItem("fine_metal", 1, 9)
				player:removeGold(4000)

				player:addItem("battle_rune", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Battle rune milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
					0
				)
			end
		elseif choice == "Light buckler" then
			player:dialogSeq(
				{
					t,
					"Light buckler adalah buckler yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Whisper bracelet, satu Scroll of defense, dan 12,000 emas."
				},
				1
			)

			if player:hasItem("whisper_bracelet", 1) ~= true or player:hasItem(
				"scroll_of_defense",
				1
			) ~= true or player.money < 12000 then
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			local choice2 = player:menuSeq(
				"Kulihat semuanya sudah kau bawa. Mau kubuatkan sekarang?",
				{"Ya", "Tidak"},
				{}
			)

			if choice2 == 1 then
				-- yes

				if player:hasItem("whisper_bracelet", 1) ~= true or player:hasItem(
					"scroll_of_defense",
					1
				) ~= true or player.money < 12000 then
					player:dialogSeq(
						{
							t,
							"Kembalilah kepadaku kalau barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("whisper_bracelet", 1, 9)
				player:removeItem("scroll_of_defense", 1, 9)
				player:removeGold(12000)

				player:addItem("light_buckler", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Light buckler milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
					0
				)
			end
		elseif choice == "Basic buckler" then
			player:dialogSeq(
				{
					t,
					"Basic buckler adalah buckler yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of Defense sebagai cetakan, satu Blood untuk menampung kekuatan yang ditempa ke dalamnya, dan 24,000 emas."
				},
				1
			)

			if player:hasItem("blood", 1) ~= true or player:hasItem("scroll_of_defense", 1) ~= true or player.money < 24000 then
				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			local choice2 = player:menuSeq(
				"Kulihat semuanya sudah kau bawa. Mau kubuatkan sekarang?",
				{"Ya", "Tidak"},
				{}
			)

			if choice2 == 1 then
				-- yes

				if player:hasItem("blood", 1) ~= true or player:hasItem("scroll_of_defense", 1) ~= true or player.money < 24000 then
					player:dialogSeq(
						{
							t,
							"Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("blood", 1, 9)
				player:removeItem("scroll_of_defense", 1, 9)
				player:removeGold(24000)

				player:addItem("basic_buckler", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Basic buckler milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
					0
				)
			end
		elseif choice == "Heavy buckler" then
			player:dialogSeq(
				{
					t,
					"Heavy buckler adalah buckler yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of defense sebagai cetakan, satu Il san Blood untuk menampung kekuatan yang ditempa ke dalamnya, dan 48,000 emas."
				},
				1
			)

			if player:hasItem("il_san_blood", 1) ~= true or player:hasItem(
				"scroll_of_defense",
				1
			) ~= true or player.money < 48000 then
				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			local choice2 = player:menuSeq(
				"Kulihat semuanya sudah kau bawa. Mau kubuatkan sekarang?",
				{"Ya", "Tidak"},
				{}
			)

			if choice2 == 1 then
				-- yes

				if player:hasItem("il_san_blood", 1) ~= true or player:hasItem(
					"scroll_of_defense",
					1
				) ~= true or player.money < 48000 then
					player:dialogSeq(
						{
							t,
							"Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("il_san_blood", 1, 9)
				player:removeItem("scroll_of_defense", 1, 9)
				player:removeGold(48000)

				player:addItem("heavy_buckler", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Heavy buckler milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
					0
				)
			end
		elseif choice == "Amber buckler" then
			player:dialogSeq(
				{
					t,
					"Amber buckler adalah buckler yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of defense sebagai cetakan, satu EE san Blood untuk menampung kekuatan yang ditempa ke dalamnya, dan 96,000 emas."
				},
				1
			)

			if player:hasItem("ee_san_blood", 1) ~= true or player:hasItem(
				"scroll_of_defense",
				1
			) ~= true or player.money < 96000 then
				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			local choice2 = player:menuSeq(
				"Kulihat semuanya sudah kau bawa. Mau kubuatkan sekarang?",
				{"Ya", "Tidak"},
				{}
			)

			if choice2 == 1 then
				-- yes

				if player:hasItem("ee_san_blood", 1) ~= true or player:hasItem(
					"scroll_of_defense",
					1
				) ~= true or player.money < 96000 then
					player:dialogSeq(
						{
							t,
							"Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("ee_san_blood", 1, 9)
				player:removeItem("scroll_of_defense", 1, 9)
				player:removeGold(96000)

				player:addItem("amber_buckler", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Amber buckler milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
					0
				)
			end
		elseif choice == "Enchanted buckler" then
			player:dialogSeq(
				{
					t,
					"Enchanted buckler adalah buckler yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of defense sebagai cetakan, satu Sam san Blood untuk menampung kekuatan yang ditempa ke dalamnya, dan 192,000 emas."
				},
				1
			)

			if player:hasItem("sam_san_blood", 1) ~= true or player:hasItem(
				"scroll_of_defense",
				1
			) ~= true or player.money < 192000 then
				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			local choice2 = player:menuSeq(
				"Kulihat semuanya sudah kau bawa. Mau kubuatkan sekarang?",
				{"Ya", "Tidak"},
				{}
			)

			if choice2 == 1 then
				-- yes

				if player:hasItem("sam_san_blood", 1) ~= true or player:hasItem(
					"scroll_of_defense",
					1
				) ~= true or player.money < 192000 then
					player:dialogSeq(
						{
							t,
							"Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("sam_san_blood", 1, 9)
				player:removeItem("scroll_of_defense", 1, 9)
				player:removeGold(192000)

				player:addItem("enchanted_buckler", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Enchanted buckler milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
					0
				)
			end
		elseif choice == "Mystic buckler" then
			player:dialogSeq(
				{
					t,
					"Mystic buckler adalah buckler yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of defense sebagai cetakan, satu Sa san Blood untuk menampung kekuatan yang ditempa ke dalamnya, 500 Ri shard, 250 Xi shard, 100 Zen shard, dan 384,000 emas."
				},
				1
			)

			if player:hasItem("sa_san_blood", 1) ~= true or player:hasItem(
				"scroll_of_defense",
				1
			) ~= true or player:hasItem("ri_shard", 500) ~= true or player:hasItem(
				"xi_shard",
				250
			) ~= true or player:hasItem("zen_shard", 100) ~= true or player.money < 384000 then
				player:dialogSeq(
					{t, "Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."},
					0
				)
				return
			end

			local choice2 = player:menuSeq(
				"Kulihat semuanya sudah kau bawa. Mau kubuatkan sekarang?",
				{"Ya", "Tidak"},
				{}
			)

			if choice2 == 1 then
				-- yes

				if player:hasItem("sa_san_blood", 1) ~= true or player:hasItem(
					"scroll_of_defense",
					1
				) ~= true or player:hasItem("ri_shard", 500) ~= true or player:hasItem(
					"xi_shard",
					250
				) ~= true or player:hasItem("zen_shard", 100) ~= true or player.money < 384000 then
					player:dialogSeq(
						{
							t,
							"Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("sa_san_blood", 1, 9)
				player:removeItem("scroll_of_defense", 1, 9)
				player:removeItem("ri_shard", 500, 9)
				player:removeItem("xi_shard", 250, 9)
				player:removeItem("zen_shard", 100, 9)
				player:removeGold(384000)

				player:addItem("mystic_buckler", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Mystic buckler milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
					},
					0
				)
			elseif choice2 == 2 then
				-- no
				player:dialogSeq(
					{t, "Kembalilah kepadaku kalau kau berubah pikiran."},
					0
				)
			end
		end
	end)
}
