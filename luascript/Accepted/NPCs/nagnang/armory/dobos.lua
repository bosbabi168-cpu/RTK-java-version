DobosNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.baseClass ~= 1 then
			player:dialogSeq({t, "Maaf, aku tidak bisa menolong kaummu."}, 0)
			return
		end

		if not player:hasLegend("nagnang_warrior_trial") then
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
				"Aku ingin kau membalaskan dendamku pada Nagnag atas apa yang ia lakukan padaku. Satu-satunya yang bisa kubantu adalah membuatkanmu barang-barang yang dulu kubuat untuk para warriors-nya."
			},
			1
		)

		local items = {}

		if player.level >= 11 then
			table.insert(items, "War amulet")
		end
		if player.level >= 39 then
			table.insert(items, "War rune")
		end
		if player.level >= 69 then
			table.insert(items, "Bamboo shield")
		end
		if player.level >= 97 then
			table.insert(items, "Stone shield")
		end
		if player.level >= 99 and player.mark >= 1 then
			table.insert(items, "Hide shield")
		end
		if player.level >= 99 and player.mark >= 2 then
			table.insert(items, "Brass shield")
		end
		if player.level >= 99 and player.mark >= 3 then
			table.insert(items, "Titanium shield")
		end
		if player.level >= 99 and player.mark >= 4 then
			table.insert(items, "Noble shield")
		end

		local choice = player:menuString(
			"Apa yang ingin kubantu buatkan?",
			items
		)

		if choice == "War amulet" then
			player:dialogSeq(
				{
					t,
					"War amulet adalah amulet yang luar biasa, sangat berkuasa.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Earth scalemail, 10 bear's liver, satu herb pipe, dan 1,000 emas."
				},
				1
			)

			if player:hasItem("earth_scale_mail", 1) ~= true or player:hasItem(
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

				if player:hasItem("earth_scale_mail", 1) ~= true or player:hasItem(
					"bears_liver",
					10
				) ~= true or player:hasItem("herb_pipe", 1) ~= true or player.money < 1000 then
					return
				end

				player:removeItem("earth_scale_mail", 1, 9)
				player:removeItem("bears_liver", 10, 9)
				player:removeItem("herb_pipe", 1, 9)
				player:removeGold(1000)

				player:addItem("war_amulet", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, War amulet milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "War rune" then
			player:dialogSeq(
				{
					t,
					"War rune adalah rune yang luar biasa, sangat berkuasa.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Hunang's axe, satu Fine metal, dan 4,000 emas."
				},
				1
			)

			if player:hasItem("hunangs_axe", 1) ~= true or player:hasItem("fine_metal", 1) ~= true or player.money < 4000 then
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

				if player:hasItem("hunangs_axe", 1) ~= true or player:hasItem("fine_metal", 1) ~= true or player.money < 4000 then
					return
				end

				player:removeItem("hunangs_axe", 1, 9)
				player:removeItem("fine_metal", 1, 9)
				player:removeGold(4000)

				player:addItem("war_rune", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini rune Perang milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Bamboo shield" then
			player:dialogSeq(
				{
					t,
					"Bamboo shield adalah shield yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Titanium glove, satu Tall shield, dan 12,000 emas."
				},
				1
			)

			if player:hasItem("titanium_glove", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player.money < 12000 then
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

				if player:hasItem("titanium_glove", 1) ~= true or player:hasItem(
					"tall_shield",
					1
				) ~= true or player.money < 12000 then
					return
				end

				player:removeItem("titanium_glove", 1, 9)
				player:removeItem("tall_shield", 1, 9)
				player:removeGold(12000)

				player:addItem("bamboo_shield", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini rune Perang milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Stone shield" then
			player:dialogSeq(
				{
					t,
					"Stone shield adalah shield yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Tall shield sebagai cetakan, satu Spike untuk menampung kekuatan yang ditempa ke dalamnya, dan 24,000 emas."
				},
				1
			)

			if player:hasItem("spike", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player.money < 24000 then
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

				if player:hasItem("spike", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player.money < 24000 then
					return
				end

				player:removeItem("spike", 1, 9)
				player:removeItem("tall_shield", 1, 9)
				player:removeGold(24000)

				player:addItem("stone_shield", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Stone shield milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Hide shield" then
			player:dialogSeq(
				{
					t,
					"Hide shield adalah shield yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Tall shield sebagai cetakan, satu Il san Spike untuk menampung kekuatan yang ditempa ke dalamnya, dan 48,000 emas."
				},
				1
			)

			if player:hasItem("il_san_spike", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player.money < 48000 then
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

				if player:hasItem("il_san_spike", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player.money < 48000 then
					return
				end

				player:removeItem("il_san_spike", 1, 9)
				player:removeItem("tall_shield", 1, 9)
				player:removeGold(48000)

				player:addItem("hide_shield", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Hide shield milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Brass shield" then
			player:dialogSeq(
				{
					t,
					"Brass shield adalah shield yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Tall shield sebagai cetakan, satu EE san Spike untuk menampung kekuatan yang ditempa ke dalamnya, dan 96,000 emas."
				},
				1
			)

			if player:hasItem("ee_san_spike", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player.money < 96000 then
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

				if player:hasItem("ee_san_spike", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player.money < 96000 then
					return
				end

				player:removeItem("ee_san_spike", 1, 9)
				player:removeItem("tall_shield", 1, 9)
				player:removeGold(96000)

				player:addItem("brass_shield", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Brass shield milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Titanium shield" then
			player:dialogSeq(
				{
					t,
					"Titanium shield adalah shield yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Tall shield sebagai cetakan, satu Sam san Spike untuk menampung kekuatan yang ditempa ke dalamnya, dan 192,000 emas."
				},
				1
			)

			if player:hasItem("sam_san_spike", 1) ~= true or player:hasItem(
				"tall_shield",
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

				if player:hasItem("sam_san_spike", 1) ~= true or player:hasItem(
					"tall_shield",
					1
				) ~= true or player.money < 192000 then
					return
				end

				player:removeItem("sam_san_spike", 1, 9)
				player:removeItem("tall_shield", 1, 9)
				player:removeGold(192000)

				player:addItem("titanium_shield", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Titanium shield milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
		elseif choice == "Noble shield" then
			player:dialogSeq(
				{
					t,
					"Noble shield adalah shield yang luar biasa; berat, tetapi mampu menyerap banyak pukulan untukmu.",
					"Sayangnya ketika Nagnag dan pasukannya pergi dari sini, mereka membawa semuanya; seluruh persediaanku habis. Kalau kau menginginkannya, kau harus membawakan bahan-bahannya kepadaku.",
					"Untuk membuatnya aku butuh satu Tall shield sebagai cetakan, satu Sa san Spike untuk menampung kekuatan yang ditempa ke dalamnya, 500 Ri shard, 250 Xi shard, 100 Zen shard, dan 384,000 emas."
				},
				1
			)

			if player:hasItem("sa_san_spike", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player:hasItem(
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

				if player:hasItem("sa_san_spike", 1) ~= true or player:hasItem("tall_shield", 1) ~= true or player:hasItem(
					"ri_shard",
					500
				) ~= true or player:hasItem("xi_shard", 250) ~= true or player:hasItem(
					"zen_shard",
					100
				) ~= true or player.money < 384000 then
					return
				end

				player:removeItem("sa_san_spike", 1, 9)
				player:removeItem("tall_shield", 1, 9)
				player:removeItem("ri_shard", 500, 9)
				player:removeItem("xi_shard", 250, 9)
				player:removeItem("zen_shard", 100, 9)
				player:removeGold(384000)

				player:addItem("noble_shield", 1, 0, player.ID)

				player:dialogSeq(
					{
						t,
						"Nah, ini dia, Noble shield milikmu sendiri. Semoga ia membawa keberuntungan dalam pertempuran melawan Nagnag dan segala kejahatan!"
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
