CarnageHallShopNpc = {
	click = async(function(player, npc)
		local opts = {"Beli", "Jual", "Pulangkan aku"}

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)

		if choice == "Beli" then
			player:buyExtend(
				"I think I can accommodate some of the things you need. What would you like?",
				CarnageHallShopNpc.buyItems(npc)
			)
		elseif choice == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				CarnageHallShopNpc.sellItems(npc)
			)
		elseif choice == "Pulangkan aku" then
			local confirm = player:menuSeq(
				"Kau yakin ingin pergi?",
				{"Ya", "Tidak"},
				{}
			)

			if confirm == 1 then
				player.gfxDye = 0
				player:returnToInn()
			end
		end
	end),

	buyItems = function(npc)
		local buyItems = {"bears_liver", "antler", "herb_pipe"}

		return buyItems
	end,

	sellItems = function(npc)
		local buyItems = CarnageHallShopNpc.buyItems(npc)
		return buyItems
	end
}
