JiongNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.baseClass ~= 4 then
			player:dialogSeq({t, "Maaf, aku tidak bisa menolong kaummu."}, 0)
			return
		end

		if not player:hasLegend("destroyed_nagnang_evil") then
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
				"Aku ingin kau membalaskan dendamku pada Nagnag atas apa yang ia lakukan padaku. Satu-satunya yang bisa kubantu adalah membuatkanmu barang-barang yang dulu kubuat untuk para poets-nya."
			},
			1
		)

		local items = {}

		if player.level >= 11 then
			table.insert(items, "Love amulet")
		end
		if player.level >= 39 then
			table.insert(items, "Love rune")
		end
		if player.level >= 69 then
			table.insert(items, "Nature charm")
		end
		if player.level >= 97 then
			table.insert(items, "Soul charm")
		end
		if player.level >= 99 and player.mark >= 1 then
			table.insert(items, "Spirit charm")
		end
		if player.level >= 99 and player.mark >= 2 then
			table.insert(items, "Love charm")
		end
		if player.level >= 99 and player.mark >= 3 then
			table.insert(items, "Life charm")
		end
		if player.level >= 99 and player.mark >= 4 then
			table.insert(items, "Immortality charm")
		end

		local choice = player:menuString(
			"Apa yang ingin kubantu buatkan?",
			items
		)

		if choice == "Love amulet" then
			player:dialogSeq(
				{
					t,
					"Love amulet adalah amulet yang luar biasa, sangat berkuasa.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh Earth robe, 10 bear's liver, satu herb pipe, dan 1,000 emas."
				},
				1
			)

			if player:hasItem("earth_robes", 1) ~= true or player:hasItem("bears_liver", 10) ~= true or player:hasItem(
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

				if player:hasItem("earth_robes", 1) ~= true or player:hasItem("bears_liver", 10) ~= true or player:hasItem(
					"herb_pipe",
					1
				) ~= true or player.money < 1000 then
					return
				end

				player:removeItem("earth_robes", 1, 9)
				player:removeItem("bears_liver", 10, 9)
				player:removeItem("herb_pipe", 1, 9)
				player:removeGold(1000)

				player:addItem("love_amulet", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Love amulet milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Love rune" then
			player:dialogSeq(
				{
					t,
					"Love rune adalah rune yang luar biasa, sangat berkuasa.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Death's head, satu Fine metal, dan 4,000 emas."
				},
				1
			)

			if player:hasItem("deaths_head", 1) ~= true or player:hasItem("fine_metal", 1) ~= true or player.money < 4000 then
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

				if player:hasItem("deaths_head", 1) ~= true or player:hasItem("fine_metal", 1) ~= true or player.money < 4000 then
					return
				end

				player:removeItem("deaths_head", 1, 9)
				player:removeItem("fine_metal", 1, 9)
				player:removeGold(4000)

				player:addItem("love_rune", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Love rune milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Nature charm" then
			player:dialogSeq(
				{
					t,
					"Nature charm adalah ward yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Sen glove, satu Lantern, dan 12,000 emas."
				},
				1
			)

			if player:hasItem("sen_glove", 1) ~= true or player:hasItem("lantern", 1) ~= true or player.money < 12000 then
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

				if player:hasItem("sen_glove", 1) ~= true or player:hasItem("lantern", 1) ~= true or player.money < 12000 then
					return
				end

				player:removeItem("sen_glove", 1, 9)
				player:removeItem("lantern", 1, 9)
				player:removeGold(12000)

				player:addItem("nature_charm", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Nature charm milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Soul charm" then
			player:dialogSeq(
				{
					t,
					"Soul charm adalah charm yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Lantern sebagai cetakan, satu Charm untuk menampung kekuatan yang ditempa ke dalamnya, dan 24,000 emas."
				},
				1
			)

			if player:hasItem("lantern", 1) ~= true or player:hasItem("charm", 1) ~= true or player.money < 24000 then
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

				if player:hasItem("lantern", 1) ~= true or player:hasItem("charm", 1) ~= true or player.money < 24000 then
					return
				end

				player:removeItem("lantern", 1, 9)
				player:removeItem("charm", 1, 9)
				player:removeGold(24000)

				player:addItem("soul_charm", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Soul charm milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Spirit charm" then
			player:dialogSeq(
				{
					t,
					"Spirit charm adalah charm yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Lantern sebagai cetakan, satu Il san Charm untuk menampung kekuatan yang ditempa ke dalamnya, dan 48,000 emas."
				},
				1
			)

			if player:hasItem("lantern", 1) ~= true or player:hasItem("il_san_charm", 1) ~= true or player.money < 48000 then
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

				if player:hasItem("lantern", 1) ~= true or player:hasItem("il_san_charm", 1) ~= true or player.money < 48000 then
					return
				end

				player:removeItem("lantern", 1, 9)
				player:removeItem("il_san_charm", 1, 9)
				player:removeGold(48000)

				player:addItem("spirit_charm", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Spirit charm milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Love charm" then
			player:dialogSeq(
				{
					t,
					"Love charm adalah charm yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Lantern sebagai cetakan, satu Ee san Charm untuk menampung kekuatan yang ditempa ke dalamnya, dan 96,000 emas."
				},
				1
			)

			if player:hasItem("lantern", 1) ~= true or player:hasItem("ee_san_charm", 1) ~= true or player.money < 96000 then
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

				if player:hasItem("lantern", 1) ~= true or player:hasItem("ee_san_charm", 1) ~= true or player.money < 96000 then
					return
				end

				player:removeItem("lantern", 1, 9)
				player:removeItem("ee_san_charm", 1, 9)
				player:removeGold(96000)

				player:addItem("love_charm", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Love charm milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Life charm" then
			player:dialogSeq(
				{
					t,
					"Life charm adalah charm yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Lantern sebagai cetakan, satu Sam san Charm untuk menampung kekuatan yang ditempa ke dalamnya, dan 192,000 emas."
				},
				1
			)

			if player:hasItem("lantern", 1) ~= true or player:hasItem("sam_san_charm", 1) ~= true or player.money < 192000 then
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

				if player:hasItem("lantern", 1) ~= true or player:hasItem("sam_san_charm", 1) ~= true or player.money < 192000 then
					return
				end

				player:removeItem("lantern", 1, 9)
				player:removeItem("sam_san_charm", 1, 9)
				player:removeGold(192000)

				player:addItem("life_charm", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Life charm milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Immortality charm" then
			player:dialogSeq(
				{
					t,
					"Immortality charm adalah charm yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Lantern sebagai cetakan, satu Sa san Charm untuk menampung kekuatan yang ditempa ke dalamnya, 500 Ri shard, 250 Xi shard, 100 Zen shard, dan 384,000 emas."
				},
				1
			)

			if player:hasItem("lantern", 1) ~= true or player:hasItem("sa_san_charm", 1) ~= true or player:hasItem(
				"ri_shard",
				500
			) ~= true or player:hasItem("xi_shard", 250) ~= true or player:hasItem(
				"zen_shard",
				100
			) ~= true or player.money < 384000 then
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

				if player:hasItem("lantern", 1) ~= true or player:hasItem("sa_san_charm", 1) ~= true or player:hasItem(
					"ri_shard",
					500
				) ~= true or player:hasItem("xi_shard", 250) ~= true or player:hasItem(
					"zen_shard",
					100
				) ~= true or player.money < 384000 then
					return
				end

				player:removeItem("lantern", 1, 9)
				player:removeItem("sa_san_charm", 1, 9)
				player:removeItem("ri_shard", 500, 9)
				player:removeItem("xi_shard", 250, 9)
				player:removeItem("zen_shard", 100, 9)
				player:removeGold(384000)

				player:addItem("immortality_charm", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Immortality charm milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
