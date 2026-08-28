MiscGameShopNpc = {
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

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				MiscGameShopNpc.buyItems()
			)
		elseif menu == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				MiscGameShopNpc.sellItems()
			)
		end
	end),

	buyItems = function()
		local buyItems = {"limestone", "obsidian"}

		return buyItems
	end,

	sellItems = function()
		return MiscGameShopNpc.buyItems()
	end
}
