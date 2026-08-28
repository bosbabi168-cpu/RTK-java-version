sage_5_reward = {
	use = async(function(player)
		local item = player:getInventoryItem(player.invSlot)

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		if not player:canCast(1, 1, 0) then
			player:sendMinitext("Kau tidak bisa memakai ini.")
			return
		end

		if player:hasSpell("sages_wisdom") then
			player:dialogSeq({t, "Kau sudah punya Sages Wisdom (Sage 5)."}, 0)
			return
		end

		local confirm = player:menuSeq(
			"Kau yakin ingin menambahkan Sages Wisdom (Sage 5) ke karaktermu? Ini akan menggantikan seluruh versi sage sebelumnya.",
			{"Ya", "Tidak"},
			{}
		)

		if confirm == 1 then
			if player:hasItem("sage_5_reward", 1) ~= true then
				player:dialogSeq(
					{t, "Barang Sage 5 Reward tidak ada padamu."},
					0
				)
				return
			end

			player:removeItem("sage_5_reward", 1)

			player:addSpell("sages_wisdom")
		end
	end)
}
