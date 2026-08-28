ElixirShopNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual"}

		if npc.mapTitle == "Elixir Hall" then
			table.insert(opts, "Pergi")
		end

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)

		local buyItems = {"acorn"}
		local sellItems = {"acorn"}

		if choice == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				buyItems
			)
		elseif choice == "Jual" then
			player:sellExtend("What are you willing to sell today?", sellItems)
		elseif choice == "Pergi" then
			clone.wipe(player)
			player.registry["elixirTeam"] = 0
			player:returnFunc()
		end
	end)
}
