InnNpc2 = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local opts = {
			"RetroTK untuk Pemula",
			"Perjalanan",
			"Tanggal & Waktu"
		}

		local choice = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if choice == "RetroTK untuk Pemula" then
			general_npc_funcs.novices(player, npc)
		elseif choice == "Perjalanan" then
			Waypoint.click(player, npc)
		elseif choice == "Tanggal & Waktu" then
			general_npc_funcs.time(player)
		end
	end),

	onSayClick = async(function(player, npc)
		Waypoint.onSayClick(player, npc)
	end)
}
