experience_envelope_pack = {
	use = function(player)
		local item = player:getInventoryItem(player.invSlot)

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		if player.exp < 1000000000 then
			-- 1 bil
			player:sendMinitext("Pengalamanmu tidak cukup untuk membuat amplop.")
			return
		end

		if not player:hasSpace("full_experience_envelope", 1, player.ID) then
			player:sendMinitext("Kantongmu penuh.")
			return
		end

		player.exp = player.exp - 1000000000
		player:sendStatus()

		player:addItem("full_experience_envelope", 1, 0, player.ID)
	end
}

experience_envelope_pack_1_day = {
	use = function(player)
		experience_envelope_pack.use(player)
	end
}
experience_envelope_pack_7_days = {
	use = function(player)
		experience_envelope_pack.use(player)
	end
}
experience_envelope_pack_30_days = {
	use = function(player)
		experience_envelope_pack.use(player)
	end
}

full_experience_envelope = {
	use = function(player)
		local item = player:getInventoryItem(player.invSlot)

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		if player.exp + 1000000000 > 4294967295 then
			-- max bar
			player:dialogSeq(
				{t, "Pengalamanmu terlalu banyak. Jual sebagian lalu coba lagi."},
				0
			)
			return
		end

		player.exp = player.exp + 1000000000
		player:sendStatus()

		player:removeItem("full_experience_envelope", 1)
	end
}
