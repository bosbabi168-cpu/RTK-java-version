AlignmentNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local shrine = ""
		local alignCheck = 0

		if npc.mapTitle == "Kwi-sin Shrine" then
			shrine = "Kwi-Sin"
			alignCheck = 1
		elseif npc.mapTitle == "Ming-ken Shrine" then
			shrine = "Ming-ken"
			alignCheck = 2
		elseif npc.mapTitle == "Ohaeng Shrine" then
			shrine = "Ohaeng"
			alignCheck = 3
		end

		if player.level < 50 then
			player:dialogSeq(
				{t, "Kau belum siap membuat pilihan ini. Kembalilah nanti."},
				0
			)
			return
		end

		if player.alignment ~= 0 then
			if player.alignment ~= alignCheck then
				player:dialogSeq(
					{t, "Kau bukan " .. shrine .. ", aku tidak bisa menolongmu."},
					0
				)
				return
			elseif player.alignment == alignCheck then
				player:dialogSeq(
					{
						t,
						"Pergilah, anak muda, dan beri tahu guild master-mu bahwa kau memilih menjadi " .. shrine
					},
					0
				)
				return
			end
		end

		player:dialogSeq(
			{
				t,
				"Greetings, magic user.",
				"Kau ingin menemukan sifat sejatimu?",
				"Pertama aku harus tahu bahwa kau memahami sifat jiwamu."
			},
			1
		)

		local choice
		if shrine == "Kwi-Sin" then
			choice = player:menuSeq(
				"Ada berapa sifat yang bisa dipilih?",
				{
					"Hanya satu, yaitu dirimu.",
					"Dua, kau dan Ming-Ken",
					"Tiga, kau, Ming-Ken, dan Ohaeng."
				},
				{}
			)
		elseif shrine == "Ming-ken" then
			choice = player:menuSeq(
				"Ada berapa sifat yang bisa dipilih?",
				{
					"Hanya satu, yaitu dirimu.",
					"Dua, kau dan Ming-Ken",
					"Tiga, kau, Ohaeng, dan Kwi-Sin."
				},
				{}
			)
		elseif shrine == "Ohaeng" then
			choice = player:menuSeq(
				"Ada berapa sifat yang bisa dipilih?",
				{
					"Hanya satu, yaitu dirimu.",
					"Dua, kau dan Ming-Ken",
					"Tiga, kau, Ming-Ken, dan Kwi-Sin."
				},
				{}
			)
		end

		if choice == 1 or choice == 2 then
			player:dialogSeq(
				{
					t,
					"Kau keliru, jumlahnya tiga. Pelajarilah dirimu dan sifat-sifat itu sebelum melangkah terlalu jauh. Kau hanya boleh memilih sekali, selamanya!"
				},
				0
			)
			return
		elseif choice == 3 then
			player:dialogSeq(
				{
					t,
					"Ya, ya. Orang bijak, kau mulai memahami sifat-sifat itu. Tetapi masih ada yang perlu diketahui sebelum kau mengabdikan diri pada salah satunya seumur hidup."
				},
				1
			)

			local subChoice = player:menuSeq(
				"Tiap sifat mewakili sisi kekuatan yang berbeda; apa yang dilakukan " .. shrine .. " represent?",
				{
					shrine .. " adalah sifat kehidupan.",
					shrine .. " adalah keseimbangan segalanya.",
					shrine .. " adalah sifat alam baka."
				},
				{}
			)
			local correctChoice

			if shrine == "Kwi-Sin" then
				correctChoice = 3
			elseif shrine == "Ming-ken" then
				correctChoice = 1
			elseif shrine == "Ohaeng" then
				correctChoice = 2
			end

			if subChoice ~= correctChoice then
				player:dialogSeq(
					{
						t,
						"Kau keliru; kau perlu mempelajari sifat-sifat itu.",
						"Ming-Ken adalah sifat kehidupan.",
						"Ohaeng adalah keseimbangan segalanya.",
						"Kwi-Sin adalah sifat alam baka.",
						"Pergilah dan belajarlah lagi sebelum mengambil langkah ini."
					},
					0
				)
				return
			elseif subChoice == correctChoice then
				player:dialogSeq(
					{
						t,
						"Ya, kau memang mengerti. Kau sedang dalam perjalanan menjadi " .. shrine
					},
					1
				)

				local subSubChoice = player:menuSeq(
					"Ini kesempatan terakhirmu; dari sini tidak ada jalan kembali, kau hanya boleh memilih sekali seumur hidup.",
					{
						"Aku tidak ingin menjadi " .. shrine,
						"Aku perlu berpikir dulu.",
						"Aku mau sifat yang lain.",
						"Aku akan mengabdikan diri seumur hidup."
					},
					{}
				)

				if subSubChoice >= 1 and subSubChoice <= 3 then
					player:dialogSeq(
						{
							t,
							"Bijak sekali kau tidak terburu-buru pada sesuatu yang akan memengaruhi seluruh sisa hidupmu."
						},
						0
					)
					return
				elseif subSubChoice == 4 then
					local alignment = 0

					if shrine == "Kwi-Sin" then
						alignment = 1
					elseif shrine == "Ming-ken" then
						alignment = 2
					elseif shrine == "Ohaeng" then
						alignment = 3
					end

					player:swapAlignment(alignment)
					player:dialogSeq(
						{
							t,
							"Kini kau mengabdi pada sifat " .. shrine .. ", beri tahu Guildmaster-mu tentang keputusanmu."
						},
						0
					)
					return
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
	end
}
