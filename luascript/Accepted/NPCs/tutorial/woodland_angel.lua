WoodlandAngelNpc = {
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
				"Akhirnya kau sampai juga.\n\nIni menandai akhir latihanmu bersama kami. Para tutor besar di kota akan melanjutkan latihanmu.",
				"Ingatlah, masih sangat banyak yang belum kau pelajari. Pastikan kau membaca hukum tanah ini dan menaatinya. Pelajari juga segala yang ditawarkan para tutor.",
				"Kalau kau mati di luar sana, tekan Shift + <F1> lalu pilih 'Silver Thread'; kau bisa memilih pergi ke salah satu Dukun yang akan menghidupkanmu kembali.\n\nKalau butuh bantuan lain, pakai <F1>",
				"Yang terakhir kuajarkan adalah cara berbicara dan berbisik kepada orang lain. Untuk berbicara kepada semua orang di sekitarmu, ketik ' (ini tombol \" tanpa menekan shift).\n\nUntuk berbisik kepada seseorang, tekan \" lalu ketik namanya.",
				"Untuk keluar dari daerah ini, pakai tombol ' lalu ucapkan kata 'Selesaikan'."
			},
			{}
		)
		player.registry["basic_tutorial_complete"] = 1
	end),

	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		if speech == "selesaikan" then
			local t = {
				graphic = convertGraphic(npc.look, "monster"),
				color = npc.lookColor
			}
			player.npcGraphic = t.graphic
			player.npcColor = t.color
			player.dialogType = 0
			player.lastClick = npc.ID

			Tools.checkKarma(player)

			if player.registry["basic_tutorial_complete"] == 0 then
				player.registry["basic_tutorial_complete"] = 1
			end

			if not player:hasSpell("gateway") then
				player:addSpell("gateway")
			end

			player:dialogSeq(
				{
					t,
					"Semoga berhasil; kini kuserahkan kau ke tangan para tutor kota, JadeSpear dan Ironheart.",
					"Sekarang saatnya memilih negerimu. Jangan khawatir kalau kau belum mengenal negeri-negeri itu; kau selalu bisa mengubahnya nanti."
				},
				1
			)

			local choice = player:menuSeq(
				"Di mana kau ingin tinggal?",
				{"Neutral", "Koguryo", "Buya", "Aku belum tahu"},
				{}
			)

			--local choice = player:menuSeq("Where do you wish to live?",{"Koguryo","Buya","I don't know yet"},{})

			if choice ~= nil then
				player.registry["mignokexp"] = 0
				player.registry["tominaru1exp"] = 0
				player.registry["tominaru2exp"] = 0
				player.registry["tominaru3exp"] = 0
				player.registry["tominaru4exp"] = 0
				player.registry["tominaru5exp"] = 0
				player.registry["tutorialnpcexp"] = 0

				player:dialogSeq(
					{
						t,
						"Sekarang kau akan kami kirim ke tutor kota dan kami beri mantra khusus untuk berkeliling Kerajaan-kerajaan ini. Semoga berhasil."
					},
					1
				)
			end

			if choice == 2 or choice == 3 then
				player:updateCountry(choice - 1)
			elseif choice == 5 then
				player:dialogSeq(
					{
						t,
						"Aku mengerti kau belum mau pergi dari sini. Temui aku lagi kalau kau berubah pikiran."
					},
					0
				)
				return
			else
				player:dialogSeq(
					{t, "Untuk saat ini kau harus memilih Koguryo atau Buya."},
					0
				)
				return
			end

			if player.country == 1 then
				-- Koguryo
				player:warp(36, 3, 7)
			elseif player.country == 2 then
				-- Buya
				player:warp(351, 6, 9)
			end
		end
	end)
}
