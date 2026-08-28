qui_hyang = {
	use = async(function(player)
		local t = {graphic = convertGraphic(309, "item"), color = 0}

		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		if player.warpOut == 0 then
			player:sendMinitext("Tidak bisa berpindah keluar.")
			return
		end

		if player.state == 1 then
			player:sendMinitext("Kau butuh tubuh jasmani untuk memakai gulungan kuning ini.")
			return
		end

		local opts = {"Pulang"}

		if player.clan ~= 0 then
			table.insert(opts, "Clan hall")
		end

		if player.class >= 10 then
			table.insert(opts, "Subpath Circle")
		end

		table.insert(opts, "Main Inn")

		local choice = player:menuString("Ke mana kau ingin pergi?", opts)

		if choice == "Pulang" then
			player:returnFunc()
			return
		elseif choice == "Subpath Circle" then
			player:returnToSubpath()
			return
		elseif choice == "Clan hall" then
			player:returnToClan()
			return
		elseif choice == "Main Inn" then
			player:returnToInn()
		else
			return
		end
	end)
}
