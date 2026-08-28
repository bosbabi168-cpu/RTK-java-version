vex_mage = {
	cast = function(player, target)
		local duration = 425000
		local magicCost = 60

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Itu sudah tidak berguna lagi.")
			return
		end

		if (target.blType == BL_PC and not player:canPK(target)) or target.blType == BL_NPC then
			player:sendMinitext("Kau tidak bisa menyerang sasaran itu.")
			return
		end

		if target:checkIfCast(curses) then
			player:sendMinitext("Mantra lain sejenis ini sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu sudah terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(43)
		player:sendMinitext("Kau merapal Vex.")
		target:setDuration("vex_mage", duration)
		target:sendAnimation(1, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 30
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Vex padamu.")
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
		target.armor = target.armor + 30
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 30
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 44
		local items = {Item("acorn").id, Item("lucky_coin").id, 0}
		local itemAmounts = {70, 1, 250}
		local description = "Gives +30AC to target."
		return level, items, itemAmounts, description
	end
}

deaths_face_mage = {
	cast = function(player, target)
		local duration = 425000
		local magicCost = 60

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Itu sudah tidak berguna lagi.")
			return
		end

		if (target.blType == BL_PC and not player:canPK(target)) or target.blType == BL_NPC then
			player:sendMinitext("Kau tidak bisa menyerang sasaran itu.")
			return
		end

		if target:checkIfCast(curses) then
			player:sendMinitext("Mantra lain sejenis ini sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu sudah terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(43)
		player:sendMinitext("Kau merapal Death's Face.")
		target:setDuration("deaths_face_mage", duration)
		target:sendAnimation(53, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 30
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Death's Face padamu.")
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
		target.armor = target.armor + 30
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 30
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 44
		local items = {Item("acorn").id, Item("lucky_coin").id, 0}
		local itemAmounts = {70, 1, 250}
		local description = "Gives +30AC to target."
		return level, items, itemAmounts, description
	end
}

unnatural_selection_mage = {
	cast = function(player, target)
		local duration = 425000
		local magicCost = 60

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Itu sudah tidak berguna lagi.")
			return
		end

		if (target.blType == BL_PC and not player:canPK(target)) or target.blType == BL_NPC then
			player:sendMinitext("Kau tidak bisa menyerang sasaran itu.")
			return
		end

		if target:checkIfCast(curses) then
			player:sendMinitext("Mantra lain sejenis ini sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu sudah terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(44)
		player:sendMinitext("Kau merapal Unnatural Selection.")
		target:setDuration("unnatural_selection_mage", duration)
		target:sendAnimation(101, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 30
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Unnatural Selection padamu.")
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
		target.armor = target.armor + 30
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 30
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 44
		local items = {Item("acorn").id, Item("lucky_coin").id, 0}
		local itemAmounts = {70, 1, 250}
		local description = "Gives +30AC to target."
		return level, items, itemAmounts, description
	end
}

flaw_mage = {
	cast = function(player, target)
		local duration = 425000
		local magicCost = 60

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Itu sudah tidak berguna lagi.")
			return
		end

		if (target.blType == BL_PC and not player:canPK(target)) or target.blType == BL_NPC then
			player:sendMinitext("Kau tidak bisa menyerang sasaran itu.")
			return
		end

		if target:checkIfCast(curses) then
			player:sendMinitext("Mantra lain sejenis ini sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu sudah terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(26)
		player:sendMinitext("Kau merapal Flaw.")
		target:setDuration("flaw_mage", duration)
		target:sendAnimation(79, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 30
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Flaw padamu.")
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
		target.armor = target.armor + 30
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 30
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 44
		local items = {Item("acorn").id, Item("lucky_coin").id, 0}
		local itemAmounts = {70, 1, 250}
		local description = "Gives +30AC to target."
		return level, items, itemAmounts, description
	end
}
