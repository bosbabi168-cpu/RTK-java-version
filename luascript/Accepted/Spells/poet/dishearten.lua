dishearten_poet = {
	cast = function(player, target)
		local duration = 18000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if (target.blType == BL_PC and not player:canPK(target)) or target.blType == BL_NPC then
			player:sendMinitext("Kau tidak bisa menyerang sasaran itu.")
			return
		end

		if target:checkIfCast(bolsters) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if target:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(5)
		player:sendMinitext("Kau merapal Dishearten.")
		target:setDuration("dishearten_poet", duration)
		target:sendAnimation(1, 0)
		if target.blType == BL_PC then
			target:sendMinitext(player.name .. " merapal Dishearten padamu.")
			target:calcStat()
		elseif target.blType == BL_MOB then
			target.armor = target.armor + 6
		end
	end,

	recast = function(block)
		block.armor = block.armor + 6
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor - 6
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {150000}
		local description = "Weaken a target's armor."
		return level, items, itemAmounts, description
	end
}

dark_fear_poet = {
	cast = function(player, target)
		local duration = 18000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target:checkIfCast(bolsters) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if target:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(43)
		player:sendMinitext("Kau merapal Dark Fear.")
		target:setDuration("dark_fear_poet", duration)
		target:sendAnimation(53, 0)
		if target.blType == BL_PC then
			target:sendMinitext(player.name .. " merapal Dark Fear padamu.")
			target:calcStat()
		elseif target.blType == BL_MOB then
			target.armor = target.armor + 6
		end
	end,

	recast = function(block)
		block.armor = block.armor + 6
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor - 6
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {150000}
		local description = "Weaken a target's armor."
		return level, items, itemAmounts, description
	end
}

break_will_poet = {
	cast = function(player, target)
		local duration = 18000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target:checkIfCast(bolsters) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if target:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(26)
		player:sendMinitext("Kau merapal Break Will.")
		target:setDuration("break_will_poet", duration)
		target:sendAnimation(101, 0)
		if target.blType == BL_PC then
			target:sendMinitext(player.name .. " merapal Break Will padamu.")
			target:calcStat()
		elseif target.blType == BL_MOB then
			target.armor = target.armor + 6
		end
	end,

	recast = function(block)
		block.armor = block.armor + 6
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor - 6
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {150000}
		local description = "Weaken a target's armor."
		return level, items, itemAmounts, description
	end
}

harshen_attack_poet = {
	cast = function(player, target)
		local duration = 18000
		local magicCost = 1000

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target:checkIfCast(bolsters) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end
		if target:checkIfCast(disheartens) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		if target:checkIfCast(protections) then
			player:sendMinitext("Sasaran itu terlindungi.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(26)
		player:sendMinitext("Kau merapal Harshen Attack.")
		target:setDuration("harshen_attack_poet", duration)
		target:sendAnimation(79, 0)
		if target.blType == BL_PC then
			target:sendMinitext(player.name .. " merapal Harshen Attack padamu.")
			target:calcStat()
		elseif target.blType == BL_MOB then
			target.armor = target.armor + 6
		end
	end,

	recast = function(block)
		block.armor = block.armor + 6
		block:sendStatus()
	end,
	uncast = function(block)
		block.armor = block.armor - 6
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {0}
		local itemAmounts = {150000}
		local description = "Weaken a target's armor."
		return level, items, itemAmounts, description
	end
}
