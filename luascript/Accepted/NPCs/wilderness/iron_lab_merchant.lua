IronLabMerchantNpc = {
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
				"Salam, Petualang, selamat datang di kedalaman gua ini.",
				"Kau bisa menemukan banyak harta di dalam kotak-kotak ini kalau kau membukanya dengan Iron Key-mu."
			},
			1
		)
		if player:hasItem("iron_key", 1) == true then
			local opts = {
				"Ya, aku bersedia menjual kuncinya kepadamu.",
				"Tidak, kunciku mau kusimpan."
			}
			local menu = player:menuString(
				"Kau mau menjual kunci yang kau punya kepadaku seharga 300 emas?",
				opts
			)

			if menu == opts[1] then
				player:removeItem("iron_key", 1, 9)
				player.money = player.money + 300
				player:sendStatus()
				player:dialogSeq({t, "Terima kasih, ini dia."}, 0)
			else
				player:dialogSeq(
					{
						t,
						"Selamat jalan kalau begitu, kawan. Semoga petualanganmu lancar."
					},
					0
				)
			end
		else
			player:dialogSeq(
				{
					t,
					"Kau bisa menemukannya tersebar di antara makhluk yang tinggal di sini."
				},
				0
			)
		end
	end)
}
