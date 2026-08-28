sanctuary_poet = {
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
			target:setDuration("sanctuary_poet", duration)
			target:sendAnimation(11, 0)
			if target.id ~= player.id then
				target:sendMinitext("Kau menemukan Sanctuary.")
				target:sendMinitext(player.name .. " merapal Sanctuary padamu.")
			end

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
		local level = 30
		local items = {Item("scroll").id, Item("ink").id}
		local itemAmounts = {1, 1}
		local desc = "Reduces all damage taken by 1/2."
		return level, items, itemAmounts, desc
	end
}

protect_soul_poet = {
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
			target:setDuration("protect_soul_poet", duration)
			target:sendAnimation(61, 0)
			if target.id ~= player.id then
				target:sendMinitext("Kau menemukan Sanctuary.")
				target:sendMinitext(player.name .. " merapal Protect Soul padamu.")
			end
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
		local level = 30
		local items = {Item("scroll").id, Item("ink").id}
		local itemAmounts = {1, 1}
		local desc = "Reduces all damage taken by 1/2."
		return level, items, itemAmounts, desc
	end
}

guard_life_poet = {
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
			target:setDuration("guard_life_poet", duration)
			target:sendAnimation(56, 0)
			if target.id ~= player.id then
				target:sendMinitext("Kau menemukan Sanctuary.")
				target:sendMinitext(player.name .. " merapal Guard Life padamu.")
			end
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
		local level = 30
		local items = {Item("scroll").id, Item("ink").id}
		local itemAmounts = {1, 1}
		local desc = "Reduces all damage taken by 1/2."
		return level, items, itemAmounts, desc
	end
}

magic_shield_poet = {
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
			target:setDuration("magic_shield_poet", duration)
			target:sendAnimation(59, 0)
			if target.id ~= player.id then
				target:sendMinitext("Kau menemukan Sanctuary.")
				target:sendMinitext(player.name .. " merapal Magic Shield padamu.")
			end
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
		local level = 30
		local items = {Item("scroll").id, Item("ink").id}
		local itemAmounts = {1, 1}
		local desc = "Reduces all damage taken by 1/2."
		return level, items, itemAmounts, desc
	end
}
