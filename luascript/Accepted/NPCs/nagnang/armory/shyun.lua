ShyunNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.baseClass ~= 3 then
			player:dialogSeq({t, "Maaf, aku tidak bisa menolong kaummu."}, 0)
			return
		end

		if not player:hasLegend("family_nangen_mages") then
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
				"Aku ingin kau membalaskan dendamku pada Nagnag atas apa yang ia lakukan padaku. Satu-satunya yang bisa kubantu adalah membuatkanmu barang-barang yang dulu kubuat untuk para magi-nya."
			},
			1
		)

		local items = {}

		if player.level >= 11 then
			table.insert(items, "Magic amulet")
		end
		if player.level >= 39 then
			table.insert(items, "Magic rune")
		end
		if player.level >= 69 then
			table.insert(items, "Magic ward")
		end
		if player.level >= 97 then
			table.insert(items, "Mages ward")
		end
		if player.level >= 99 and player.mark >= 1 then
			table.insert(items, "Enchanted ward")
		end
		if player.level >= 99 and player.mark >= 2 then
			table.insert(items, "Conjurer's ward")
		end
		if player.level >= 99 and player.mark >= 3 then
			table.insert(items, "Mysticism ward")
		end
		if player.level >= 99 and player.mark >= 4 then
			table.insert(items, "Masters ward")
		end

		local choice = player:menuString(
			"Apa yang ingin kubantu buatkan?",
			items
		)

		if choice == "Magic amulet" then
			player:dialogSeq(
				{
					t,
					"Magic amulet adalah amulet yang luar biasa, sangat berkuasa.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh Earth cloth, 10 bear's liver, satu herb pipe, dan 1,000 emas."
				},
				1
			)

			if player:hasItem("earth_clothes", 1) ~= true or player:hasItem(
				"bears_liver",
				10
			) ~= true or player:hasItem("herb_pipe", 1) ~= true or player.money < 1000 then
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

				if player:hasItem("earth_clothes", 1) ~= true or player:hasItem(
					"bears_liver",
					10
				) ~= true or player:hasItem("herb_pipe", 1) ~= true or player.money < 1000 then
					player:dialogSeq(
						{
							t,
							"Kembalilah kepadaku kalau barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("earth_clothes", 1, 9)
				player:removeItem("bears_liver", 10, 9)
				player:removeItem("herb_pipe", 1, 9)
				player:removeGold(1000)

				player:addItem("magic_amulet", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Magic amulet milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Magic rune" then
			player:dialogSeq(
				{
					t,
					"Magic rune adalah rune yang luar biasa, sangat berkuasa.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Bekyun's spear, satu Fine metal, dan 4,000 emas."
				},
				1
			)

			if player:hasItem("bekyuns_spear", 1) ~= true or player:hasItem("fine_metal", 1) ~= true or player.money < 4000 then
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

				if player:hasItem("bekyuns_spear", 1) ~= true or player:hasItem("fine_metal", 1) ~= true or player.money < 4000 then
					player:dialogSeq(
						{
							t,
							"Kembalilah kepadaku kalau barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("bekyuns_spear", 1, 9)
				player:removeItem("fine_metal", 1, 9)
				player:removeGold(4000)

				player:addItem("magic_rune", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Magic rune milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Magic ward" then
			player:dialogSeq(
				{
					t,
					"Magic ward adalah ward yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of invocation, satu Holy ring, dan 12,000 emas."
				},
				1
			)

			if player:hasItem("scroll_of_invocation", 1) ~= true or player:hasItem(
				"holy_ring",
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

				if player:hasItem("scroll_of_invocation", 1) ~= true or player:hasItem(
					"holy_ring",
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

				player:removeItem("scroll_of_invocation", 1, 9)
				player:removeItem("holy_ring", 1, 9)
				player:removeGold(12000)

				player:addItem("magic_ward", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Magic ward milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Mages ward" then
			player:dialogSeq(
				{
					t,
					"Mages ward adalah ward yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of invocation sebagai cetakan, satu Surge untuk menampung kekuatan yang ditempa ke dalamnya, dan 24,000 emas."
				},
				1
			)

			if player:hasItem("scroll_of_invocation", 1) ~= true or player:hasItem(
				"surge",
				1
			) ~= true or player.money < 24000 then
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

				if player:hasItem("scroll_of_invocation", 1) ~= true or player:hasItem(
					"surge",
					1
				) ~= true or player.money < 24000 then
					player:dialogSeq(
						{
							t,
							"Silakan kembali kalau seluruh barang yang diperlukan sudah kau bawa."
						},
						0
					)
					return
				end

				player:removeItem("scroll_of_invocation", 1, 9)
				player:removeItem("surge", 1, 9)
				player:removeGold(24000)

				player:addItem("mages_ward", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Mages ward milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Enchanted ward" then
			player:dialogSeq(
				{
					t,
					"Enchanted ward adalah ward yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of invocation sebagai cetakan, satu Il san Surge untuk menampung kekuatan yang ditempa ke dalamnya, dan 48,000 emas."
				},
				1
			)

			if player:hasItem("scroll_of_invocation", 1) ~= true or player:hasItem(
				"il_san_surge",
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

				if player:hasItem("scroll_of_invocation", 1) ~= true or player:hasItem(
					"il_san_surge",
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

				player:removeItem("il_san_surge", 1, 9)
				player:removeItem("scroll_of_invocation", 1, 9)
				player:removeGold(48000)

				player:addItem("enchanted_ward", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Enchanted ward milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Conjurer's ward" then
			player:dialogSeq(
				{
					t,
					"Conjurer's ward adalah ward yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of invocation sebagai cetakan, satu Ee san Surge untuk menampung kekuatan yang ditempa ke dalamnya, dan 96,000 emas."
				},
				1
			)

			if player:hasItem("ee_san_surge", 1) ~= true or player:hasItem(
				"scroll_of_invocation",
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

				if player:hasItem("ee_san_surge", 1) ~= true or player:hasItem(
					"scroll_of_invocation",
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

				player:removeItem("ee_san_surge", 1, 9)
				player:removeItem("scroll_of_invocation", 1, 9)
				player:removeGold(96000)

				player:addItem("conjurers_ward", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Conjurer's ward milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Mysticism ward" then
			player:dialogSeq(
				{
					t,
					"Mysticism ward adalah ward yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of invocation sebagai cetakan, satu Sam san Surge untuk menampung kekuatan yang ditempa ke dalamnya, dan 192,000 emas."
				},
				1
			)

			if player:hasItem("sam_san_surge", 1) ~= true or player:hasItem(
				"scroll_of_invocation",
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
				if player:hasItem("sam_san_surge", 1) ~= true or player:hasItem(
					"scroll_of_invocation",
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

				player:removeItem("sam_san_surge", 1, 9)
				player:removeItem("scroll_of_invocation", 1, 9)
				player:removeGold(192000)

				player:addItem("mysticism_ward", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Mysticism ward milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Masters ward" then
			player:dialogSeq(
				{
					t,
					"Masters ward adalah ward yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Scroll of invocation sebagai cetakan, satu Sa san Surge untuk menampung kekuatan yang ditempa ke dalamnya, 500 Ri shard, 250 Xi shard, 100 Zen shard, dan 384,000 emas."
				},
				1
			)

			if player:hasItem("sa_san_surge", 1) ~= true or player:hasItem(
				"scroll_of_invocation",
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

				if player:hasItem("sa_san_surge", 1) ~= true or player:hasItem(
					"scroll_of_invocation",
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

				player:removeItem("sa_san_surge", 1, 9)
				player:removeItem("scroll_of_invocation", 1, 9)
				player:removeItem("ri_shard", 500, 9)
				player:removeItem("xi_shard", 250, 9)
				player:removeItem("zen_shard", 100, 9)
				player:removeGold(384000)

				player:addItem("masters_ward", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Masters ward milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
