curse = {
	cast = function(player, target)
		local duration = 50000
		local magicCost = 60

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

		if target:checkIfCast(curses) or target.cursed == 1 then
			player:sendMinitext("Mantra lain sejenis ini sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu sudah terlindungi.")
			return
		end

		if target:hasDuration("snare_trap") then
			target:setDuration("snare_trap", 0)
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(43)
		player:sendMinitext("Kau merapal Curse.")
		target:setDuration("curse", duration)
		target:sendAnimation(1, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 35
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Curse padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
		if (block.blType == BL_MOB and block.charState ~= 2) then
			block:sendAnimation(34, 0)
		elseif (block.blType == BL_PC and block.state ~= 2) then
			block:sendAnimation(34, 0)
		end
	end,
	recast = function(target)
		target.armor = target.armor + 35
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 35
		target.cursed = 0
		target:sendStatus()
	end
}
