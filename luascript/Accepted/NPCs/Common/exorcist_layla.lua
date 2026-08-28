ExorcistLaylaNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 6 then
			player:dialogSeq(
				{
					t,
					"Menakjubkan, kristal-kristal ini benar-benar memikat...",
					"Andai saja Spoon bisa mengambilkanku beberapa contoh..."
				},
				1
			)
		end
		if player.quest["reeves_quest"] == 8 then
			player:dialogSeq(
				{
					t,
					"Aku tidak akan pernah percaya sesuatu sejahat itu bisa mencengkeram dunia kita. Kristal yang kau berikan memberiku gagasan cara memutus cengkeraman Calamity pada patung itu. Aku hanya butuh sedikit waktu lagi.",
					"Andai Spoon tidak begitu acuh, ini mungkin bisa selesai lebih cepat..."
				},
				1
			)
		end

		if player.quest["reeves_quest"] == 6 then
			player.quest["reeves_quest"] = 7
		end
		if player.quest["reeves_quest"] == 7 then
			if player:hasItem("crystal_shard", 1) ~= true then
				return
			end

			local choice = player:menuString(
				"Luar biasa! Aku bahkan tidak akan bertanya bagaimana kau bisa memperoleh serpihan ini! Boleh kupakai untuk kupelajari?",
				{"Ya", "Tidak sudi!"}
			)
			if choice == "Ya" then
				player:dialogSeq(
					{
						t,
						"Indah, indah sekali! Aku akan segera memulai penyelidikanku. Akan kuberitahu Spoon bahwa kita bisa memulai ritual untuk menyegel Calamity di dalam patung itu selamanya."
					},
					1
				)
				if player:hasItem("crystal_shard", 1) ~= true then
					return
				end
				player:removeItem("crystal_shard", 1)
				player.quest["reeves_quest"] = 8
			end
			if choice == "Tidak sudi!" then
				player:dialogSeq(
					{
						t,
						"Oh, ya sudah... entah apa yang hendak kau lakukan dengannya... jenius."
					},
					1
				)
			end
		end
	end)
}
