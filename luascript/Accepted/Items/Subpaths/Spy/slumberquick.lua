slumberquick = {
	use = function(player)
		player:removeItem("slumberquick", 1)
		local targetFacing = getTargetFacing(player, BL_MOB)
		if targetFacing ~= nil then
			if targetFacing.yname == "spy_hwan" then
				targetFacing:delete()
				player:addItem("slumbering_hwan", 1)
				player.quest["spy_trials"] = 11
				player.mapRegistry["hwan"] = os.time()
				hwan_spell.cast(player)
				local potion = {
					graphic = convertGraphic(982, "item"),
					color = 0
				}
				player:dialogSeq(
					{
						potion,
						"** Kau menunggu sejenak sampai ia tertidur. Kau harus menyeret tubuhnya ke tempat interogasi. Hati-hati jangan terlalu lama, atau ia akan bangun!"
					},
					0
				)
			end
		end
	end
}

hwan_spell = {
	cast = function(player)
		player:setDuration("hwan_spell", 240000)
		player:setTimer(2, 240)
	end,
	uncast = function(player)
		if player.quest["spy_trials"] == 11 then
			player:sendMinitext("Hwan escaped!")
			player:removeItem("slumbering_hwan", 1)
		end
	end
}
