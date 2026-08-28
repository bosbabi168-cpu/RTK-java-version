local _getWaypointId = function(player, npc)
	local waypointIdByMap = {
		[1013] = "northern_pass",
		[1025] = "dae_shore",
		[1027] = "hausson",
		[1122] = "sanhae",
		[3812] = "arctic_village"
	}

	local waypointId = waypointIdByMap[npc.m]

	if (not waypointId) then
		return "kugnae"
	end

	return waypointId
end

InnNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local opts = {
			"Beli",
			"Jual",
			"Simpanan",
			"Perjalanan",
			"Tanggal & Waktu"
		}

		local waypointId = _getWaypointId(player, npc)

		if os.time() >= player.registry["gave_fragile_orb_of_world_shout_time"] then
			table.insert(opts, "World Shout Gratis")
		end

		if (not Waypoint.isEnabled(player, waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local choice = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if choice == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				InnNpc.buyItems()
			)
		elseif choice == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				InnNpc.sellItems()
			)
		elseif choice == "Simpanan" then
			bank.show_main_menu(player, npc)
		elseif choice == "Perjalanan" then
			Waypoint.click(player, npc)
		elseif choice == "Tanggal & Waktu" then
			general_npc_funcs.time(player)
		elseif choice == "World Shout Gratis" then
			general_npc_funcs.freeWorldShout(player, npc)
		elseif choice == "Waypoint" then
			Waypoint.add(player, npc, waypointId)
		end
	end),

	onSayClick = async(function(player, npc)
		local speech = player.speech:lower()
		local waypointId = _getWaypointId(player, npc)

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, waypointId)) then
			Waypoint.add(player, npc, waypointId)
			return
		end

		Waypoint.onSayClick(player, npc)
	end),

	buyItems = function()
		local buyItems = {
			"apple",
			"wine",
			"thick_wine",
			"yellow_scroll",
			"soup_bowl",
			"comb",
			"rice_wine",
			"root_liquor"
		}

		return buyItems
	end,

	sellItems = function()
		local sellItems = InnNpc.buyItems()

		if (Config.bossDropSalesEnabled) then
			table.insert(sellItems, "lucky_coin")
			table.insert(sellItems, "lucky_silver_coin")
		end

		return sellItems
	end
}
