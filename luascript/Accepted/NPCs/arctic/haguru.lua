HaguruNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		--if player.registry["tutorial_quest"] == 11 and player:killCount("mountain_wolf") >= 3 then
		if player:killCount("mountain_wolf") >= 3 then
			player.quest["helped_haguru"] = 1

			player:dialogSeq(
				{
					t,
					"Kau hebat! Terima kasih banyak! Sekarang aku bisa menyelamatkan kawan-kawanku di atas sana, begitu mereka keluar dari persembunyian.",
					"Namaku Haguru, saudara para tutor kota besar itu.",
					"Kenapa kau tampak terkejut? Jangan bilang ia mengutusmu mencariku! Kembalilah dan katakan padanya aku baik-baik saja, tidak usah mengkhawatirkanku.",
					"Oh, dan sekali lagi terima kasih atas seluruh bantuanmu hari ini."
				},
				1
			)

			return
		end

		player:dialogSeq(
			{
				t,
				"Kulihat kau baru datang dari kotaku. Kau datang membantuku melawan kekuatan gelap itu?",
				"Tentu saja! Alasan apa lagi yang membuatmu berada di tempat seperti ini.",
				"Nah, kau boleh membantuku kalau mau, tetapi bisa berbahaya.",
				"Beberapa tingkat di atas gunung ini kau akan menemukan sekawanan serigala. Mereka mengurung rombongan buruku di sana.",
				"Kalau kau bisa membunuh beberapa ekor, aku bisa mulai menyelamatkan orang-orangnya. Hati-hati sekali, mereka melukai dengan parah."
			},
			1
		)
	end)
}
