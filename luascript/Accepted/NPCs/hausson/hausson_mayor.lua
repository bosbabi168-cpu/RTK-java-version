HaussonMayorNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.country ~= 1 then
			player:dialogSeq(
				{
					t,
					"Salam. Aku ingin sekali mengizinkanmu tinggal di sini, tetapi hanya orang Koguryo yang boleh tinggal di kota ini."
				},
				1
			)
		else
			if player.registry["home"] == 11 then
				local confirm = player:menuSeq(
					"Kau sudah tinggal di kedai kotaku... apa kau mau pergi secepat ini?",
					{"Ya, aku mau.", "Tidak, aku ingin tinggal."},
					{}
				)

				if confirm == 1 then
					-- leave
					player.registry["home"] = 0
					player:dialogSeq(
						{
							t,
							"Yah, tidak ada yang abadi. Semoga beruntung di kemudian hari."
						},
						0
					)
					return
				elseif confirm == 2 then
					player:dialogSeq(
						{
							t,
							"Ah, senang mendengarnya. Semoga kau menyukai layananku di sini."
						},
						0
					)
					return
				end
			else
				player:dialogSeq(
					{
						t,
						"Jadi kau ingin tinggal di kedaiku yang sederhana ini? Baiklah, ada kamar untukmu. Tapi ingat, kalau begitu kau akan selalu kembali ke sini, bukan ke kedai-kedai di kota."
					},
					1
				)
				local confirm = player:menuSeq(
					"Kau yakin ingin melakukan ini?",
					{"Ya, aku mau.", "Tidak, aku tidak mau."},
					{}
				)

				if confirm == 1 then
					player.registry["home"] = 11
					player:dialogSeq(
						{
							t,
							"Selamat datang di kedaiku, semoga kau betah di sini."
						},
						0
					)
					return
				elseif confirm == 2 then
					player:dialogSeq(
						{
							t,
							"Itu pilihanmu. Kamarnya masih banyak kalau nanti kau berubah pikiran."
						},
						0
					)
					return
				end
			end

			return
		end
	end)
}
