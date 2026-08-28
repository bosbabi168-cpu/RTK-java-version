local _waypointId = "museum"

MuseumCaretakerNpc = {
	click = async(function(player, npc)
		local caretakerDialog = Tools.configureDialog(player, npc)

		local opts = {
			"Caretaker's Gift",
			"Menyumbang ke Museum"
		}

		if player.quest["tutorial_quest"] == 13 and player.quest["visited_yon_and_weaved"] == 1 then
			table.insert(opts, "Students cap")
		end

		if (Config.bossDropSalesEnabled) then
			table.insert(opts, "Jual")
		end

		if (not Waypoint.isEnabled(player, _waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)

		local dragontooth = Item("dragons_tooth")

		local dragonToothDialog = {
			graphic = dragontooth.icon,
			color = dragontooth.iconC
		}

		if choice == "Jual" then
			MuseumCaretakerNpc.sellItems(player)
		elseif choice == "Waypoint" then
			Waypoint.add(player, npc, _waypointId)
		elseif choice == "Caretaker's Gift" then
			if player.quest["received_caretakers_gift"] == 1 then
				player:dialogSeq({caretakerDialog, "Kau sudah pernah menerima Dragon's tooth. Satu orang satu!"}, 0)
				return
			end

			player.quest["received_caretakers_gift"] = 1
			player:addItem("dragons_tooth", 1)

			player:dialogSeq(
				{
					caretakerDialog,
					"Semoga kau menikmati kunjunganmu ke Museum! Para Archon, sejarawan Qantao, dan aku bekerja sangat keras menyiapkannya untukmu."
				},
				1
			)

			player:dialogSeq(
				{
					dragonToothDialog,
					"Ini hadiah kecil nan langka sebagai kenangan kunjunganmu ke museum. Benda ini mengubahmu jadi naga untuk sesaat! Pakailah pada saat istimewa, sebab ia akan lenyap!"
				},
				1
			)
		elseif choice == "Menyumbang ke Museum" then
			local choice2 = player:menuSeq(
				"Menyumbangkan emas kepada Museum?",
				{
					"Tidak, terima kasih.",
					"Sumbang 50000 emas.",
					"Sumbang 5000 emas.",
					"Sumbang 500 emas."
				},
				{}
			)

			local gold
			local level

			if player:hasLegend("museum_contributor") then
				player:dialogSeq({caretakerDialog, "Terima kasih banyak atas sumbanganmu sebelumnya."}, 1)
				return
			end

			if choice2 == 1 then
				return
			elseif choice2 == 2 then
				gold = 50000
				level = "Benefactor"
			elseif choice2 == 3 then
				gold = 5000
				level = "Patron"
			elseif choice2 == 4 then
				gold = 500
				level = "Contributor"
			end

			if player.money < gold then
				player:dialogSeq({caretakerDialog, "Kembalilah kalau emasmu sudah cukup."}, 1)
				return
			else
				player:removeGold(gold)
				player:addLegend(
					"Museum " .. level .. " (" .. curT() .. ")",
					"museum_contributor",
					3,
					1
				)

				player:dialogSeq({caretakerDialog, "Terima kasih atas sumbanganmu yang murah hati! Datanglah berkunjung lagi."}, 1)
				return
			end
		elseif choice == "Students cap" then
			if player:hasItem("cloth", 1) ~= true then
				player:dialogSeq({caretakerDialog, "Maaf, anak muda. Aku tahu kau tidak sabar membuat topimu, tetapi kainnya harus kau buat sendiri supaya pas untukmu."}, 1)
				return
			else
				player:dialogSeq(
					{
						caretakerDialog,
						"Salam. Jadi kau datang untuk membuat Student cap? Pertama aku butuh kain yang kau buat sendiri.",
						"Ah, ini dia. Cara melipat ini kupelajari dari beberapa dokumen sejarah yang kukirim ke Perpustakaan."
					},
					1
				)

				player:removeItem("cloth", 1, 9)
				player:addItem("student_cap", 1, 0, player.ID)
			end
		end
	end),

	sellItems = function(player)
		local items = {
			"key_to_earth",
			"key_to_fire",
			"key_to_wind",
			"key_to_heaven",
			"key_to_pond",
			"key_to_thunder",
			"key_to_water",
			"key_to_mountain"
		}

		local prices = {}

		for i = 1, #items do
			table.insert(prices, math.floor(Item(items[i]).sell * 1.2))
		end

		player:sellExtend(
			"What are you willing to sell today?",
			items,
			prices
		)
	end,

	onSayClick = async(function(player, npc)
		Tools.configureDialog(player, npc)
		local speech = string.lower(player.speech)

		if speech == "peta" or speech == "pecahan" or speech == "pecahan peta" then
			if player.quest["instance"] == 6 then
				player:dialogSeq({"Inilah dia. Bawa ini kepada murid Chung Ryong dan tunjukkan sarinya"}, 1)
			end
			if player.quest["instance"] == 5 then
				if player:hasItem("dragon_essence", 5) == true then
					player:removeItem("dragon_essence", 5)
					player:addItem("chung_ryongs_might", 1)
					player.quest["instance"] = 6
					player:dialogSeq({"Inilah dia. Bawa ini kepada murid Chung Ryong dan tunjukkan sarinya"}, 1)
				else
					player:dialogSeq({"Jangan buang waktuku. Temui aku lagi kalau sari-sarinya sudah kau punya."}, 1)
				end
			end

			if player:hasItem("combined_map", 1) == true and player.quest["instance"] == 4 then
				player.quest["instance"] = 5

				player:dialogSeq(
					{
						"Kau diutus pustakawan ke sini? Menarik...",
						"Peta ini tidak seperti apa pun yang pernah kulihat. Ini pasti gunung surgawi yang melegenda itu... Baekdu",
						"Sayangnya kau tidak bisa melihat jalannya karena ia disembunyikan naga-naga purba.",
						"Namun aku bisa membantumu.",
						"Kalau jiwamu kami isi dengan kekuatan Chung Ryong, jalannya mungkin tersingkap bagimu dan kau diizinkan lewat.",
						"Kumpulkan 5 sari langka dari naga-naga mitos itu lalu kembalilah kepadaku kalau sudah kau punya."
					},
					1
				)
			end
		end

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, _waypointId)) then
			Waypoint.add(player, npc, _waypointId)
			return
		end
	end)
}
