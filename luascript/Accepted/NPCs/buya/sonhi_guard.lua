SonhiGuardNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.quest["presented_sonhi_pass"] == 0 then
			if player:hasItem("sonhi_pass", 1) ~= true then
				player:dialogSeq(
					{
						t,
						"Siapa kau? Sedang apa di sini?",
						"Ini pos pemeriksaan diplomatik. Di balik sini adalah tanah yang diklaim Sonhi. Kau tidak diizinkan masuk tanpa surat jalan dari KaKhan atau Kaming.",
						"Kalau kau punya surat jalan itu, tunjukkan. Kalau tidak, kau harus pergi!"
					},
					0
				)
				return
			end

			if player:hasItem("sonhi_pass", 1) == true then
				--player:removeItem("sonhi_pass",1)
				player.quest["presented_sonhi_pass"] = 1

				--player:dialogSeq({t,"Ah, you do have the pass"},1)
				--player:dialogSeq({t,"This pass is very suspicious. I will keep it with me, but you may still pass."},0)
				player:dialogSeq(
					{
						t,
						"Coba kulihat surat jalannya... ya... ya... nah, sepertinya semuanya beres.",
						"Ini dia, surat jalan Anda, Tuan."
					},
					1
				)

				return
			end
		elseif player.quest["presented_sonhi_pass"] == 1 then
			--player:dialogSeq({t,"This pass is very suspicious. I will keep it with me, but you may still pass."},0)
			--player:dialogSeq({t,"You may pass sir."},0)
		end
	end)
}
