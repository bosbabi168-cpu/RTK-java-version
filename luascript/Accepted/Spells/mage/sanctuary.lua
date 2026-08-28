sanctuary_mage = {
	cast = function(player, target)
		local duration = 300000
		local magicCost = 60

		if (player:canPK(target)) then
			duration = 185000
		end

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1 or target.blType == BL_MOB) then
			player:sendMinitext("Kau tidak bisa merapal itu sekarang.")
			return
		end

		if target:checkIfCast(sanctuaries) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if (target.blType == BL_PC) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(5)
			player:sendMinitext("Kau merapal Sanctuary.")
			target:setDuration("sanctuary_mage", duration)
			target:sendAnimation(11, 0)
			target:sendMinitext(player.name .. " merapal Sanctuary padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
	end,
	recast = function(target)
		target.deduction = target.deduction -.5
		target:sendStatus()
	end,
	uncast = function(target)
		target.deduction = target.deduction +.5
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 38
		local items = {Item("ambrosia").id, Item("ancient_robes").id}
		local itemAmounts = {1, 1}
		local desc = "Reduces all damage taken by 1/2."
		return level, items, itemAmounts, desc
	end
}

protect_soul_mage = {
	cast = function(player, target)
		local duration = 300000
		local magicCost = 60

		if (player:canPK(target)) then
			duration = 185000
		end

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1 or target.blType == BL_MOB) then
			player:sendMinitext("Kau tidak bisa merapal itu sekarang.")
			return
		end

		if target:checkIfCast(sanctuaries) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if (target.blType == BL_PC) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(106)
			player:sendMinitext("Kau merapal Protect Soul.")
			target:setDuration("protect_soul_mage", duration)
			target:sendAnimation(61, 0)
			target:sendMinitext(player.name .. " merapal Protect Soul padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
	end,
	recast = function(target)
		target.deduction = target.deduction -.5
		target:sendStatus()
	end,
	uncast = function(target)
		target.deduction = target.deduction +.5
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 38
		local items = {Item("ambrosia").id, Item("ancient_robes").id}
		local itemAmounts = {1, 1}
		local desc = "Reduces all damage taken by 1/2."
		return level, items, itemAmounts, desc
	end
}

guard_life_mage = {
	cast = function(player, target)
		local duration = 300000
		local magicCost = 60

		if (player:canPK(target)) then
			duration = 185000
		end

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1 or target.blType == BL_MOB) then
			player:sendMinitext("Kau tidak bisa merapal itu sekarang.")
			return
		end

		if target:checkIfCast(sanctuaries) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if (target.blType == BL_PC) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(77)
			player:sendMinitext("Kau merapal Guard Life.")
			target:setDuration("guard_life_mage", duration)
			target:sendAnimation(56, 0)
			target:sendMinitext(player.name .. " merapal Guard Life padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
	end,
	recast = function(target)
		target.deduction = target.deduction -.5
		target:sendStatus()
	end,
	uncast = function(target)
		target.deduction = target.deduction +.5
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 38
		local items = {Item("ambrosia").id, Item("ancient_robes").id}
		local itemAmounts = {1, 1}
		local desc = "Reduces all damage taken by 1/2."
		return level, items, itemAmounts, desc
	end
}

magic_shield_mage = {
	cast = function(player, target)
		local duration = 300000
		local magicCost = 60

		if (player:canPK(target)) then
			duration = 185000
		end

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1 or target.blType == BL_MOB) then
			player:sendMinitext("Kau tidak bisa merapal itu sekarang.")
			return
		end

		if target:checkIfCast(sanctuaries) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if (target.blType == BL_PC) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(70)
			player:sendMinitext("Kau merapal Magic Shield.")
			target:setDuration("magic_shield_mage", duration)
			target:sendAnimation(59, 0)
			target:sendMinitext(player.name .. " merapal Magic Shield padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
	end,
	recast = function(target)
		target.deduction = target.deduction -.5
		target:sendStatus()
	end,
	uncast = function(target)
		target.deduction = target.deduction +.5
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 38
		local items = {Item("ambrosia").id, Item("ancient_robes").id}
		local itemAmounts = {1, 1}
		local desc = "Reduces all damage taken by 1/2."
		return level, items, itemAmounts, desc
	end
}
