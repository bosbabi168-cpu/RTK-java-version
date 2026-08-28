ValleyFarmGhostNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.quest["valley_farm_ghost_can_hear"] == 1 then
			player.quest["majhum_told_about_bridge"] = 1
			player:dialogSeq(
				{
					t,
					"Halo?",
					"Kau bisa mendengarku?",
					"Astaga! Akhirnya ada yang bisa memahamiku. Tahukah kau betapa sulitnya mencari teman bicara saat kau jadi hantu?",
					"Aku terjebak di sini sejak pasukan mengerikan itu menyerang dan membunuh segala yang terlihat.",
					"Sekarang aku terjebak di sini, tidak sudi meninggalkan pos yang diberikan Raja Yuri sendiri kepadaku.",
					"Kenapa kau di sini? Kau tidak kelihatan seperti bagian dari pasukan besar.",
					"Lagi pula mereka menutup jembatannya dan memasang jebakan di seluruh badannya! Satu-satunya cara menyeberang adalah lewat jembatan lain, atau membuatnya sendiri."
				},
				0
			)
			return
		end

		player.quest["valley_farm_ghost_clicked"] = 1
		player:dialogSeq(
			{
				t,
				"",
				"",
				"",
				"",
				"((Hantu itu tampak berusaha bicara, tetapi kau tidak mendengar apa-apa.))"
			},
			0
		)
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

		if speech == "jembatan" then
			Tools.checkKarma(player)

			if player.quest["majhum_told_about_bridge"] == 2 then
				if player:hasItem("ginko_wood", 10) ~= true then
					player:dialogSeq(
						{
							t,
							"Aku butuh kayunya; tanpa itu kau tidak bisa membuat egrang."
						},
						0
					)
					return
				end

				if player:hasItem("wool_twine", 1) ~= true then
					player:dialogSeq(
						{
							t,
							"Aku juga butuh talinya; tanpa itu aku tidak bisa mengajarimu membuat egrang."
						},
						0
					)
					return
				end

				player:dialogSeq(
					{
						t,
						"Ah, semua yang kau butuhkan sudah ada. Bagus! Sekarang ambil talinya dan ikat kayunya seperti ini..."
					},
					1
				)

				player:removeItem("ginko_wood", 10, 9)
				player:removeItem("wool_twine", 1, 9)
				player:addItem("stilts", 1)

				player:dialogSeq(
					{
						t,
						"Nah, kau berhasil!",
						"Sekarang coba pakai, lalu masuklah ke air di sana; bagian terbaiknya persis di kiri rumah."
					},
					0
				)

				return
			end

			if player.quest["majhum_told_about_bridge"] == 1 then
				player.quest["majhum_told_about_bridge"] = 2
				player:dialogSeq(
					{
						t,
						"Membuat jembatan baru? Kurasa kau tidak bisa sendirian; itu makan waktu berbulan-bulan.",
						"Yang pertama harus kau lakukan adalah menyeberang ke sisi lain, mulai dengan tali, lalu memasang balok, lalu...",
						"Hmmm, kalau kau hanya perlu menyeberang sendiri, kau bisa memakai kiat tukang jembatan.",
						"Sungai ini cukup dalam, dan membawa perahu menembus hutan serta pegunungan itu mustahil.",
						"Kami harus memakai egrang untuk menyeberang; kalau kau membuatnya sendiri, kau bisa lewat.",
						"Egrang cukup mudah dibuat: hanya perlu beberapa potong kayu dan tali untuk mengikatnya.",
						"Bawakan aku 10 ginko wood dan tali wol yang kuat. Tali wol paling bagus kalau bakal basah; benar-benar kuat."
					},
					0
				)
			end
		end

		if speech == "dukun senja" and player.quest["majhum_told_about_bridge"] >= 1 then
			if player.registry["majhum_karma_bonus"] == 0 then
				player.registry["majhum_karma_bonus"] = 1
				player:addKarma(1.0)
				player:dialogSeq(
					{
						t,
						"Astaga, baik sekali kau menyampaikan pesannya, terima kasih."
					},
					0
				)
			else
				player:dialogSeq(
					{t, "Sekali lagi terima kasih sudah menyampaikan pesan itu."},
					0
				)
			end
		end
	end),

	move = function(npc, owner)
		local found
		local moved = true
		local oldside = npc.side
		local checkmove = math.random(0, 10)

		if (npc.retDist <= distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1 and npc.returning == false) then
			npc.returning = true
		elseif (npc.returning == true and npc.retDist > distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1) then
			npc.returning = false
		end

		if (npc.returning == true) then
			found = toStart(npc, npc.startX, npc.startY)
		else
			if (checkmove >= 4) then
				npc.side = math.random(0, 3)
				npc:sendSide()
				if (npc.side == oldside) then
					moved = npc:move()
				end
			end
		end

		if (found == true) then
			npc.returning = false
		end
	end
}
