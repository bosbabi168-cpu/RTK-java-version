iron_key = {
	use = function(player)
		local t = {graphic = convertGraphic(26, "item"), color = 0}

		if player.m ~= 1307 then
			player:sendMinitext("Kau tidak bisa memakai ini di sini.")
			return
		end

		local npc = getTargetFacing(player, BL_NPC)

		if npc.yname == "IronLabTreasureChestNpc" then
			if npc.side == 2 then
				player:removeItemSlot(player.invSlot, 1, 9)
				IronLabTreasureChestNpc.open(player, npc)
			else
				player:sendMinitext("Kuncinya sudah terbuka.")
			end
		end
	end
}
