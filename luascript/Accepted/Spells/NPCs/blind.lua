blind = {
	cast = function(player, target)
		local duration = 10000
		local magicCost = 300

		if player.blType == BL_PC then
			if (not player:canCast(1, 1, 0)) then
				return
			end

			if (player.magic < magicCost) then
				player:sendMinitext("Kehendakmu terlalu lemah.")
				return
			end

			if (target.state == 1) then
				player:sendMinitext("Itu sudah tidak berguna lagi.")
				return
			end

			if (target.blType == BL_PC and not player:canPK(target)) then
				player:sendMinitext("Kau tidak bisa menyerang sasaran itu.")
				return
			end

			if target:checkIfCast(blinds) then
				player:sendMinitext("Mantra lain sejenis ini sedang bekerja.")
				return
			end

			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(43)
			player:sendMinitext("Kau merapal Blind.")
			player:sendMinitext("Calling Blind.")
		elseif player.blType == BL_MOB then
			if target:checkIfCast(blinds) then
				return
			end
		end

		if target.blType == BL_PC then
			target:sendMinitext(player.name .. " menyerangmu dengan mantra Blind.")
		end

		target:setDuration("blind", duration)
		target.blind = true
		target:updateState()
	end,

	while_cast = function(block)
		block.blind = true
	end,

	--[[on_takedamage_while_cast = function(block)
	block:removeDuras(blinds)
end,]]
	--

	uncast = function(block)
		block.blind = false
		block:updateState()
	end
}
