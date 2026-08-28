local _waypointId = "splinter"

SplinterNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local opts = {
			"Beli",
			"Jual",
			"Keahlian Kerajinan",
			"Gathering Wood",
			"Jangan Remehkan Woodworking",
			"Woodworking Devotion"
		}

		if (not Waypoint.isEnabled(player, _waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local menu = player:menuString(
			"Halo! Apa yang ingin kau lakukan hari ini?",
			opts
		)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				SplinterNpc.buyItems()
			)
		elseif menu == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				SplinterNpc.sellItems()
			)
		elseif menu == "Keahlian Kerajinan" then
			generalNPC.crafting_skills(player, npc)
		elseif menu == "Gathering Wood" then
			player:dialogSeq(
				{
					"Ya, aku tahu soal itu. Pergilah ke tempat berpohon dan tebas dengan kapakmu.",
					"Kadang kau menemukan sesuatu. Sebaiknya kau cari 'rumpun' untuk menebang kayu. Ada beberapa di berbagai hutan.",
					"Misalnya ada satu dekat Buya di 46, 20 dan satu dekat Kugnae di 111, 178. Kurasa kau tidak akan beruntung menebang di luar rumpun."
				},
				0
			)
			return
		elseif menu == "Jangan Remehkan Woodworking" then
			player:dialogSeq(
				{
					"Keahlian yang bagus. Memang kau bisa membuat zirah apa pun, atau senjata logam yang katanya 'lebih unggul' itu. Tapi kami tukang kayu jauh lebih serbabisa.",
					"Pertukangan kayu memungkinkanmu membuat senjata kayu dan anak panah. Selain itu, pertukangan kayu dibutuhkan untuk membuat alat tenun. Kalau kau sudah siap, katakan saja 'kayu' padaku.",
					"Kalau kerjamu buruk, yang tersisa cuma rongsokan kayu. Tunjukkan padaku, tanyakan 'rongsokan' dan kita lihat apa yang masih bisa diselamatkan."
				},
				0
			)
			return
		elseif menu == "Woodworking Devotion" then
			SplinterNpc.woodworkingDevotion(player, npc)
		elseif menu == "Waypoint" then
			Waypoint.add(player, npc, _waypointId)
		end
	end),

	woodworkingDevotion = function(player, npc)
		Tools.configureDialog(player, npc)

		if (player.level < 25) then
			player:dialogSeq({"Kau belum siap menekuni satu kerajinan. Kembalilah nanti."}, 0)
			return
		end

		if crafting.checkSkillLegend(player, "woodworking") then
			player:dialogSeq({"Kau sudah menekuni ilmu Woodworking."}, 0)
			return
		end

		crafting.checkSkill(player, npc, "jewelry making")
		crafting.checkSkill(player, npc, "tailoring")
		crafting.checkSkill(player, npc, "metalworking")

		player:dialogSeq({"Tukang kayu bisa membuat senjata kayu, busur, anak panah, dan alat tenun. Kau ingin menjadi tukang kayu?"}, 1)

		crafting.addSkill(player, npc, "woodworking")
	end,

	buyItems = function()
		local buyItems = {"axe"}
		return buyItems
	end,

	sellItems = function()
		local sellItems = {
			"axe",
			"ginko_wood",
			"weaving_tools",
			"fine_weaving_tools",
			"spring_quiver",
			"summer_quiver",
			"wooden_sword",
			"viperhead_woodsaber",
			"viperhead_woodsword",
			"wooden_blade",
			"supple_wooden_sword",
			"supple_viperhead_woodsaber",
			"supple_viperhead_woodsword",
			"supple_wooden_blade",
			"oaken_sword",
			"supple_oaken_sword",
			"oaken_blade",
			"supple_oaken_blade"
		}
		return sellItems
	end,

	onSayClick = async(function(player, npc)
		Tools.configureDialog(player, npc)
		local speech = string.lower(player.speech)

		if speech == "kayu" or speech == "rongsokan" or speech == "rongsokan" then
			crafting.craftingDialog(player, npc, speech)
		end

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, _waypointId)) then
			Waypoint.add(player, npc, _waypointId)
			return
		end
	end),
}
