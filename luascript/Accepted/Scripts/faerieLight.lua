Player.faerieLight = function(player)
	local t = {graphic = convertGraphic(627, "item"), color = 0}
	player.npcGraphic = t.graphic
	player.npcColor = t.color
	player.dialogType = 0

	if not player:karmaCheck("angel") then
		player:dialogSeq({t, "Kau tidak memiliki karma Angel."}, 0)
		return
	end

	local choice = player:menuSeq(
		"Karmamu memenuhi jiwamu. Akankah ia menerangi jalan bagi semua?",
		{"Ya, tukarkan karmaku untuk itu.", "Tidak, karmaku akan kupertahankan."},
		{}
	)

	if choice == 1 then
		player:removeKarma(math.random(25, 30))
		player:addItem("faerie_light", 1, 0, player.ID)
		player:dialogSeq({t, "Semoga cahaya peri menuntun jiwamu."}, 0)
		return
	elseif choice == 2 then
		player:dialogSeq({t, "May another opportunity present itself."}, 0)
		return
	end
end
