harden_armor_poet = {
	cast = function(player, target)
		local duration = 300000
		if (target.blType == BL_PC and player:canPK(target)) then
			duration = 185000
		end
		local magicCost = 30

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

		if target:checkIfCast(hardarmors) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if (target.blType == BL_MOB) then
			return
		elseif (target.blType == BL_PC) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(5)
			player:sendMinitext("Kau merapal Harden Armor.")
			target:setDuration("harden_armor_poet", duration)
			target:sendAnimation(2)
			target:sendMinitext(player.name .. " merapal Harden Armor padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
	end,
	recast = function(target)
		target.armor = target.armor - 10
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor + 10
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 14
		local items = {Item("snake_meat").id, Item("ancient_armor").id, 0}
		local itemAmounts = {20, 1, 10}
		local description = "Casts Harden Armor on a target. Lowers AC by 10, reducing damage by 10%"
		return level, items, itemAmounts, description
	end
}

thicken_skin_poet = {
	cast = function(player, target)
		local duration = 300000
		if (target.blType == BL_PC and player:canPK(target)) then
			duration = 185000
		end
		local magicCost = 30

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

		if target:checkIfCast(hardarmors) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if (target.blType == BL_MOB) then
			return
		elseif (target.blType == BL_PC) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(5)
			player:sendMinitext("Kau merapal Thicken Skin.")
			target:setDuration("thicken_skin_poet", duration)
			target:sendAnimation(111)
			target:sendMinitext(player.name .. " merapal Thicken Skin padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
	end,
	recast = function(target)
		target.armor = target.armor - 10
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor + 10
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 14
		local items = {Item("snake_meat").id, Item("ancient_armor").id, 0}
		local itemAmounts = {20, 1, 10}
		local description = "Casts Harden Armor on a target. Lowers AC by 10, reducing damage by 10%"
		return level, items, itemAmounts, description
	end
}

shield_of_life_poet = {
	cast = function(player, target)
		local duration = 300000
		if (target.blType == BL_PC and player:canPK(target)) then
			duration = 185000
		end
		local magicCost = 30

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

		if target:checkIfCast(hardarmors) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if (target.blType == BL_MOB) then
			return
		elseif (target.blType == BL_PC) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(5)
			player:sendMinitext("Kau merapal Shield of Life.")
			target:setDuration("shield_of_life_poet", duration)
			target:sendAnimation(110)
			target:sendMinitext(player.name .. " merapal Shield of Life padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
	end,
	recast = function(target)
		target.armor = target.armor - 10
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor + 10
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 14
		local items = {Item("snake_meat").id, Item("ancient_armor").id, 0}
		local itemAmounts = {20, 1, 10}
		local description = "Casts Harden Armor on a target. Lowers AC by 10, reducing damage by 10%"
		return level, items, itemAmounts, description
	end
}

elemental_armor_poet = {
	cast = function(player, target)
		local duration = 300000
		if (target.blType == BL_PC and player:canPK(target)) then
			duration = 185000
		end
		local magicCost = 30

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

		if target:checkIfCast(hardarmors) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if (target.blType == BL_MOB) then
			return
		elseif (target.blType == BL_PC) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(5)
			player:sendMinitext("Kau merapal Elemental Armor.")
			target:setDuration("elemental_armor_poet", duration)
			target:sendAnimation(98)
			target:sendMinitext(player.name .. " merapal Elemental Armor padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
	end,
	recast = function(target)
		target.armor = target.armor - 10
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor + 10
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 14
		local items = {Item("snake_meat").id, Item("ancient_armor").id, 0}
		local itemAmounts = {20, 1, 10}
		local description = "Casts Harden Armor on a target. Lowers AC by 10, reducing damage by 10%"
		return level, items, itemAmounts, description
	end
}
