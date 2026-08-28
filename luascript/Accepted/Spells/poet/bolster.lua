bolster_poet = {
	cast = function(player, target)
		local duration = 37000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1 or target.blType ~= BL_PC) then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if target:checkIfCast(bolsters) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(5)
		player:sendMinitext("Kau merapal Bolster.")
		target:setDuration("bolster_poet", duration)
		target:sendAnimation(2, 0)
		target:sendMinitext(player.name .. " merapal Bolster padamu.")
		target:calcStat()
	end,

	recast = function(block)
		block.armor = block.armor - 4
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor + 4
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {150000}
		local description = "Improve a target's armor."
		return level, items, itemAmounts, description
	end
}

dark_armor_poet = {
	cast = function(player, target)
		local duration = 37000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1 or target.blType ~= BL_PC) then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if target:checkIfCast(bolsters) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(5)
		player:sendMinitext("Kau merapal Dark Armor.")
		target:setDuration("dark_armor_poet", duration)
		target:sendAnimation(111, 0)
		target:sendMinitext(player.name .. " merapal Dark Armor padamu.")
		target:calcStat()
	end,

	recast = function(block)
		block.armor = block.armor - 4
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor + 4
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {150000}
		local description = "Improve a target's armor."
		return level, items, itemAmounts, description
	end
}

life_armor_poet = {
	cast = function(player, target)
		local duration = 37000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1 or target.blType ~= BL_PC) then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if target:checkIfCast(bolsters) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(5)
		player:sendMinitext("Kau merapal Life Armor.")
		target:setDuration("life_armor_poet", duration)
		target:sendAnimation(110, 0)
		target:sendMinitext(player.name .. " merapal Life Armor padamu.")
		target:calcStat()
	end,

	recast = function(block)
		block.armor = block.armor - 4
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor + 4
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {150000}
		local description = "Improve a target's armor."
		return level, items, itemAmounts, description
	end
}

armor_of_elements_poet = {
	cast = function(player, target)
		local duration = 37000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1 or target.blType ~= BL_PC) then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if target:checkIfCast(bolsters) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(5)
		player:sendMinitext("Kau merapal Armor of Elements.")
		target:setDuration("armor_of_elements_poet", duration)
		target:sendAnimation(98, 0)
		target:sendMinitext(player.name .. " merapal Armor of Elements padamu.")
		target:calcStat()
	end,

	recast = function(block)
		block.armor = block.armor - 4
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor + 4
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {150000}
		local description = "Improve a target's armor."
		return level, items, itemAmounts, description
	end
}
