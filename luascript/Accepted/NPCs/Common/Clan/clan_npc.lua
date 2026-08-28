ClanNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}

		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {
			"Beli",
			"Jual",
			"Titipkan barang klan",
			"Ambil barang klan",
			"Simpanan",
			"Perjalanan",
			"Tanggal & Waktu"
		}

		if os.time() >= player.registry["gave_fragile_orb_of_world_shout_time"] then
			table.insert(opts, "World Shout Gratis")
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
		elseif choice == "Titipkan barang klan" then
			player:showClanBankDeposit(npc)
		elseif choice == "Ambil barang klan" then
			player:showClanBankWithdraw(npc)
		elseif choice == "Simpanan" then
			bank.show_main_menu(player, npc)
		elseif choice == "Perjalanan" then
			Waypoint.click(player, npc)
		elseif choice == "Tanggal & Waktu" then
			general_npc_funcs.time(player)
		elseif choice == "World Shout Gratis" then
			general_npc_funcs.freeWorldShout(player, npc)
		end
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

		return sellItems
	end,

	onSayClick = async(function(player, npc)
		Waypoint.onSayClick()
	end)
}
