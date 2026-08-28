pestilence_mage = {
	cast = function(player, target)
		local duration = 200000
		local magicCost = 20

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
		player:playSound(5)
		player:sendMinitext("Kau merapal Pestilence.")
		target:setDuration("pestilence_mage", duration)
		target:sendAnimation(1, 0)

		if (target.blType == BL_MOB) then
			target.armor = target.armor + 5
			target.cursed = 1
		elseif (target.blType == BL_PC and player:canPK(target)) then
			target:sendMinitext(player.name .. " merapal Pestilence padamu.")
			target:calcStat()
		end
	end,
	while_cast = function(block)
		block:sendAnimation(34, 0)
	end,
	recast = function(target)
		target.armor = target.armor + 5
		target.cursed = 1
		target:sendStatus()
	end,
	uncast = function(target)
		target.armor = target.armor - 5
		target.cursed = 0
		target:sendStatus()
	end,

	requirements = function(player)
		local level = 7
		local items = {"acorn", "wooden_sword", 0}
		local itemAmounts = {15, 1, 50}
		local desc = "A minor curse."
		return level, items, itemAmounts, desc
	end
}
