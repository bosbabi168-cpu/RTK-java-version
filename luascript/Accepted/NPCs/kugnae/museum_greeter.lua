MuseumGreeterNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		player:dialogSeq(
			{
				t,
				"Wah, halo, pengembara! Selamat datang di museum.",
				"Banyak hal menarik untuk dilihat di sini, dan banyak yang bisa dipelajari. Pernahkah kau bertanya-tanya bagaimana Gut mendapat namanya? Siapa Linskrae? Atau bagaimana Raja Suyo yang tamak itu mati?",
				"Kau bisa mempelajari semua itu, dan lebih banyak lagi, di museum ini!",
				"Semoga kau menikmati kunjunganmu. Ingat, kau boleh melihat segalanya, tetapi jangan menyentuh! Sebagian benda di museum ini tak ternilai harganya!",
				"Dan jangan lupa mampir ke ruang penjaga di akhir kunjungan. Pasti ada hadiah terima kasih menunggu di sana."
			},
			1
		)
	end)
}
