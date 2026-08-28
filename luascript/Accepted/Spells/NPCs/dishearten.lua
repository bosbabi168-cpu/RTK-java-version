dishearten = {
	cast = function(player)
		local duration = 18000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if player:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if player:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu sudah terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(5)
		player:sendMinitext("Kau merapal Dishearten.")
		player:setDuration("dishearten", duration)
		player:sendAnimation(1, 0)
		if player.blType == BL_PC then
			player:calcStat()
		elseif player.blType == BL_MOB then
			player.armor = target.armor + 6
		end
	end,

	recast = function(block)
		block.armor = block.armor + 6
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor - 6
		block:sendStatus()
	end
}
