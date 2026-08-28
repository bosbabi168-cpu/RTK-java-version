RogueGuildShamanNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		player:dialogSeq({t, "Kau tidak suka penampilanmu, ya?"}, 1)

		local choices = {"Face", "Gender", "Eyes"}

		local choice = player:menuString(
			"Penampilan yang mana yang tidak kau sukai?",
			choices
		)

		local reject = "Ah, I see. Appear as thou wilt."

		if choice == "Face" then
			general_npc_funcs.changeFace(player, npc)
		elseif choice == "Gender" then
			general_npc_funcs.changeGender(player, npc)
		elseif choice == "Eyes" then
			general_npc_funcs.changeEyes(player, npc)
		end
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

		if speech == "bulan" and player.level >= 50 and player.baseClass == 2 then
			Tools.checkKarma(player)

			player:dialogSeq(
				{
					t,
					"Di bawah bulan putih aku membunuh seorang berkuasa dari keluarga Ju yang berutang banyak uang padaku",
					"Namun aku masih belum puas."
				},
				1
			)

			if player.quest["white_moon_axe"] == 0 then
				local choice = player:menuString(
					"Bersediakah kau mengikat janji semacam itu?",
					{"Ya, aku siap.", "Aku sibuk. Mungkin nanti."},
					{}
				)

				if choice == "Ya, aku siap." then
					player.quest["white_moon_axe"] = 1
					player:freeAsync()
					RogueGuildShamanNpc.onSayClick(player, npc, "moon")
				elseif choice == "Aku sibuk. Mungkin nanti." then
					player:dialogSeq(
						{t, "Kalau begitu kau bukan orang yang tepat untuk tugas ini."},
						0
					)
				end
			elseif player.quest["white_moon_axe"] > 0 then
				local choice
				local choice2

				if player.quest["white_moon_axe"] == 1 then
					player:dialogSeq(
						{
							t,
							"Kalau kau tahu apa jadinya bisa dan kecepatan bila dipadukan, mungkin akan kupertimbangkan dirimu."
						},
						1
					)

					if player.registry["white_moon_axe_flushed_kills"] == 0 then
						player:flushKills("pale_scorpion")
						player:flushKills("skeleton_ju")
						player.registry["white_moon_axe_flushed_kills"] = 1
					elseif player.registry["white_moon_axe_flushed_kills"] == 1 then
						if player:killCount("pale_scorpion") >= 5 then
							if player:hasItem("lucky_coin", 1) ~= true then
								player:dialogSeq(
									{
										t,
										"Tanganmu kosong! Andai kau punya Lucky coin, mungkin aku percaya kau akan selamat."
									},
									0
								)
								return
							end
							player.quest["white_moon_axe"] = 2
							player.registry["white_moon_axe_flushed_kills"] = 0
							player:freeAsync()
							RogueGuildShamanNpc.onSayClick(player, npc, "moon")
						else
							player:dialogSeq(
								{
									t,
									"Andai kau sudah membunuh sedikitnya lima pale scorpion, kau pasti tahu."
								},
								0
							)
						end
					end
				end

				if player.quest["white_moon_axe"] == 2 then
					if player:killCount("skeleton_ju") >= 1 then
						player.quest["white_moon_axe"] = 3
						player:freeAsync()
						RogueGuildShamanNpc.onSayClick(player, npc, "moon")
					end

					player:dialogSeq(
						{
							t,
							"Ada yang berutang uang padaku. Banyak sekali.",
							"Dia sudah mati sekarang, tetapi itu tidak penting. Ini soal kehormatan.",
							"Malam pembantaian keluarga Ju itu mengguncang bumi. Senjataku dipenuhi kekuatan bulan putih.",
							"Tapi aku tidak pernah menemukan uang yang ia utangkan padaku.",
							"Kutugaskan kau menyiksa sisa-sisa kerangka keluarga itu. Semasa hidup nama mereka Ju.",
							"Hancurkan kerangka Ju, dan kita bicarakan soal kau mengambil uangku...",
							"...dan kapak yang kupakai membunuh orang berkuasa itu di bawah bulan putih."
						},
						0
					)
				end

				if player.quest["white_moon_axe"] == 3 then
					player:dialogSeq(
						{
							t,
							"Ah, siksaannya adalah musik bagiku.",
							"Tapi aku tetap harus mendapat uang yang ia utangkan, sementara yang kupunya hanya White moon axe ini.",
							"Jumlahnya lebih besar dari yang kau punya. Seluruhnya 20.000 keping. Kalau kau melunasi utang itu untukku, kapak ini kuberikan padamu."
						},
						1
					)

					choice2 = player:menuString(
						"Bersediakah kau memberiku 20.000 emas sebagai pengganti utang itu?",
						{"Ya", "Tidak"},
						{}
					)

					if choice2 == "Ya" then
						if player.money < 20000 then
							player:dialogSeq(
								{t, "Emasmu tidak cukup untuk melunasinya."},
								0
							)
							return
						end

						player:removeGold(20000)
						player:addItem("white_moon_axe", 1, 0, player.ID)
						player.quest["white_moon_axe"] = 0
						player:dialogSeq(
							{
								t,
								"Ini dia, rogue. Semoga ia mengilhamimu seperti ia mengilhamiku."
							},
							1
						)
						player:dialogSeq(
							{
								t,
								"Bulannya putih, ia sudah dinodai. Aku memperoleh sejenak ketenangan."
							},
							0
						)

						return
					elseif choice2 == "Tidak" then
						player:dialogSeq(
							{
								t,
								"Kalau begitu kau bukan orang yang tepat untuk tugas ini."
							},
							0
						)
						return
					end
				end
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
	end,
}
