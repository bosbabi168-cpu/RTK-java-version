NamelessHermitNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local country = ""

		if player.country == 1 then
			country = "Kugnae"
		elseif player.country == 2 then
			country = "Buya"
		end

		if player:hasItem("aged_wine", 1) == true then
			local choice = player:menuSeq(
				"Bersediakah kau melepas Aged wine itu?",
				{"Tentu saja!", "Aku agak membutuhkannya."},
				{}
			)

			if choice == 1 then
				player:removeItem("aged_wine", 1, 9)
				player:addItem("traveling_shoes", 1)
				player:dialogSeq(
					{
						t,
						"\"Wah, terima kasih! Ini.\" Si pertapa mengaduk-aduk peti berdebu. \"Ambil sepatu ini kalau kau mau.\""
					},
					1
				)
			elseif choice == 2 then
				player:dialogSeq(
					{t, "Si pertapa mengembuskan napas. \"Sayang sekali.\""},
					1
				)
			end
		end

		player:dialogSeq(
			{
				t,
				"Wah, halo! Tidak banyak tamu di daerah sini.",
				"Kau dari " .. country .. ", kan? Kelihatan dari lagakmu yang kekotaan."
			},
			1
		)

		local choice = player:menuSeq(
			"Jadi, pengembara. Apa yang membawamu ke sini?",
			{
				"Apa yang kau tahu tentang Ice Beast yang ditakuti itu?",
				"Aku cuma melihat-lihat."
			},
			{}
		)

		if choice == 1 then
			player:dialogSeq(
				{
					t,
					"Kau ke sini untuk Ice Beast?!? Semoga kau tidak serius. Makhluk itu sudah ada di daerah ini sejauh yang bisa kuingat.",
					"Katanya ia setengah abadi. Ia bisa dikalahkan, tetapi kemudian terbentuk lagi! Entah benar atau tidak. Aku tidak yakin ada yang pernah mengalahkannya!",
					"\"Syukurlah ada lava di antara kami. Ia di sisinya, aku di sisiku.\" Lelaki kuyu itu tertawa. \"Yah, kecuali saat aku menyelinap ke sana berburu kelinci. Tapi aku cepat sekali.\"",
					"Kalau kau melihat betapa besarnya, kau pun ingin menjauh. Sekali 'GEBUK' yang telak, tamatlah kau, kurasa.",
					"Kalau mau nasihatku, jangan usik makhluk keji itu. Dunia lebih baik dengan kau tetap hidup."
				},
				1
			)
		elseif choice == 2 then
			player:dialogSeq(
				{t, "Baiklah. Omong-omong, kalau aku jadi kau, aku tetap di sisi lava yang ini."},
				0
			)
		end
	end)
}
