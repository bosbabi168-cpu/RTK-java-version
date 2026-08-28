scourge_poet = {
	cast = function(player, target)
		local duration = 425000
		local magicCost = 90

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
		player:sendMinitext("Kau merapal Scourge.")
		target:setDuration("scourge_poet", duration)
		target:sendAnimation(1, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 50
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Scourge padamu.")
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
		target.armor = target.armor + 50
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 50
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 55
		local items = {Item("acorn").id, Item("amber").id, 0}
		local itemAmounts = {80, 2, 1000}
		local description = "Raises target +50 AC"
		return level, items, itemAmounts, description
	end
}

damage_will_poet = {
	cast = function(player, target)
		local duration = 425000
		local magicCost = 90

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
		player:sendMinitext("Kau merapal Damage Will.")
		target:setDuration("damage_will_poet", duration)
		target:sendAnimation(53, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 50
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Damage Will padamu.")
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
		target.armor = target.armor + 50
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 50
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 55
		local items = {Item("acorn").id, Item("amber").id, 0}
		local itemAmounts = {80, 2, 1000}
		local description = "Raises target +50 AC"
		return level, items, itemAmounts, description
	end
}

drop_guard_poet = {
	cast = function(player, target)
		local duration = 425000
		local magicCost = 90

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
		player:playSound(44)
		player:sendMinitext("Kau merapal Drop Guard.")
		target:setDuration("drop_guard_poet", duration)
		target:sendAnimation(101, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 50
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Drop Guard padamu.")
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
		target.armor = target.armor + 50
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 50
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 55
		local items = {Item("acorn").id, Item("amber").id, 0}
		local itemAmounts = {80, 2, 1000}
		local description = "Raises target +50 AC"
		return level, items, itemAmounts, description
	end
}

unalign_armor_poet = {
	cast = function(player, target)
		local duration = 425000
		local magicCost = 90

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

		if target:hasDuration("snare_trap") then
			target:setDuration("snare_trap", 0)
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(26)
		player:sendMinitext("Kau merapal Unalign Armor.")
		target:setDuration("unalign_armor_poet", duration)
		target:sendAnimation(79, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 50
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Unalign Armor padamu.")
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
		target.armor = target.armor + 50
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 50
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 55
		local items = {Item("acorn").id, Item("amber").id, 0}
		local itemAmounts = {80, 2, 1000}
		local description = "Raises target +50 AC"
		return level, items, itemAmounts, description
	end
}
