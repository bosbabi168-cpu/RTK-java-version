local _declineDialog = "You may not enter the kingdom until you agree to abide by its laws."

WoodlandGuardNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		player:dialogSeq(
			{
				"Warga masyarakat ini diharapkan menaati hukumnya. Kalau kau memilih menempuh jalan haram, jangan berlagak heran saat kau dipenjara atau dicekal.",
				"Bersikaplah tahu diri. Jangan mengganggu atau mencuri dari pemain lain. Laporkan cacat program dan jangan menyalahgunakannya. Jangan bagikan sandimu kepada siapa pun, termasuk staf RTK."
			}, 1)

		local menu = player:menuString(
			"Kestabilan masyarakat ini tanggung jawabmu. Bersumpahkah kau menaati hukum kami, termasuk yang tidak disebutkan tersurat di sini?",
			{"Ya", "Tidak"}
		)

		if (menu == "Tidak") then
			player:dialogSeq({_declineDialog}, 1)
			return
		end

		player:dialogSeq(
			{
				"Kerjamu bagus. Sekarang kau kuizinkan memasuki kota. Lanjutkan perjalananmu, dan tetaplah di sisi hukum yang benar.",
				"Untuk membantumu melawan para pelanggar hukum dan makhluk yang lebih tangguh, ini pedang yang memukul sedikit lebih keras daripada tongkat yang kau bawa itu."
			},
			1
		)

		player:addItem("novice_sword", 1)
		player:warp(4718, 10, 16)
	end)
}
