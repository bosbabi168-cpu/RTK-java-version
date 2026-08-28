SyaNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local options = {"Beli", "Jual"}
		local buyItems = {"sonhi_cloak", "sonhi_dress", "magic_mirror"}
		local sellItems = {
			"sonhi_cloak",
			"sonhi_dress",
			"magic_mirror",
			"summer_mantle",
			"autumn_mantle",
			"winter_mantle",
			"ancient_mantle",
			"blood_mantle",
			"earth_mantle",
			"summer_drapery",
			"autumn_drapery",
			"winter_drapery",
			"ancient_drapery",
			"leather_drapery",
			"earth_drapery"
		}

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			options
		)

		if choice == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				buyItems
			)
		elseif choice == "Jual" then
			player:sellExtend("What are you willing to sell today?", sellItems)
		end
	end),

	buyItems = function()
		local buyItems = {"sonhi_cloak", "sonhi_dress", "magic_mirror"}

		return buyItems
	end,

	sellItems = function()
		local sellItems = {
			"sonhi_cloak",
			"sonhi_dress",
			"magic_mirror",
			"summer_mantle",
			"autumn_mantle",
			"winter_mantle",
			"ancient_mantle",
			"blood_mantle",
			"earth_mantle",
			"summer_drapery",
			"autumn_drapery",
			"winter_drapery",
			"ancient_drapery",
			"leather_drapery",
			"earth_drapery"
		}

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

		if speech == "lewat" then
			Tools.checkKarma(player)

			if player.quest["wind_armor"] == 0 then
				player:dialogSeq(
					{
						t,
						"Ehh... aku sungguh tidak paham apa yang kau bicarakan."
					},
					0
				)
				return
			end

			if player.quest["talked_to_sya"] == 0 then
				player.quest["talked_to_sya"] = 1
				player:dialogSeq(
					{
						t,
						"Kau gila? Kau mau segel KaMing sendiri?",
						"Kau pikir ia meninggalkan segelnya di saku baju waktu terakhir mampir ke tokoku?",
						"Atau mungkin ia menitipkannya padaku sebagai tanda hormat, seperti ...oh...hmmmm...",
						"Seberapa besar sebenarnya kau menginginkan segel ini?",
						"Mungkin aku tahu sesuatu tentang seseorang, yang tahu sesuatu tentang orang lain, yang tahu sesuatu tentang segel itu.",
						"Tapi aku tidak akan membocorkan keterangan seakurat itu semudah ini... biar kupikir dulu...",
						"Aku sedang menambal beberapa pakaian tua, tetapi alat tenunku sudah aus.",
						"Kalau kau mau mengambilkan sepasang lagi, mungkin rasa terima kasihku cukup untuk menolongmu."
					},
					0
				)
				return
			end

			if player.quest["talked_to_sya"] == 1 then
				if player.quest["gave_weaving_tools_sya"] == 0 then
					if player:hasItem("fine_weaving_tools", 1) ~= true then
						player:dialogSeq(
							{
								t,
								"Aku sedang menambal beberapa pakaian tua, tetapi alat tenunku sudah aus.",
								"Kalau kau mau mengambilkan sepasang lagi, mungkin rasa terima kasihku cukup untuk menolongmu."
							},
							0
						)
						return
					end

					player:removeItem("fine_weaving_tools", 1, 9)
					player.quest["gave_weaving_tools_sya"] = 1
				end

				player:dialogSeq(
					{
						t,
						"Oh, ini bagus! Ini akan sangat meningkatkan mutu kerjaku.",
						"Hmmm...well, as promised...",
						"Blood, prajurit yang punya toko tepat di selatan sini, sempat menggumamkan sesuatu tentang orang lain yang pernah melihat segel itu.",
						"Selain itu aku tidak tahu lebih banyak. Tapi aku sudah menaruhmu di jalur yang benar; sekarang kau tahu harus bertanya kepada siapa"
					},
					0
				)
				return
			end
		end

		if speech == "kantong air" then
			player:dialogSeq(
				{
					t,
					"Oh, tolong jangan ingatkan aku pada mereka!",
					"Waktu tumbuh besar bersama orang tuaku, berpindah-pindah di seluruh gurun, yang bisa kami minum hanya air dari kantong kulit serigala."
				},
				1
			)

			if player:hasItem("wolf_pelt", 1) ~= true then
				player:dialogSeq(
					{
						t,
						"Itu salah satu alasan aku memutuskan menetap di sini ketika kesempatannya datang; aku sudah tidak tahan."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Kulihat kau membawa kulit serigala, dan dari kabar yang kudengar kau butuh bantuanku membuat kantong air."
				},
				1
			)

			if player.money < 500 then
				player:dialogSeq(
					{
						t,
						"Membuatnya menghabiskan benang dan waktuku, jadi aku harus meminta setidaknya 500 emas."
					},
					0
				)
				return
			end

			local choice = player:menuSeq(
				"Membuatnya menghabiskan benang dan waktuku; bersediakah kau membayar 500 emas?",
				{"Ya", "Tidak"},
				{}
			)

			if choice == 1 then
				player:removeGold(500)
				player:removeItem("wolf_pelt", 1, 9)
				player:addItem("empty_water_skin", 1)

				player:dialogSeq({t, "Terima kasih!"}, 0)
			end
		end
	end)
}
