static_mage = {
	cast = function(player, target)
		local duration = 15000
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

		if (target.paralyzed) then
			player:sendMinitext("Ada mantra yang lebih kuat sedang bekerja.")
			return
		end

		if (target.blType == BL_MOB) then
			player:sendAction(6, 20)
			player.magic = player.magic - magicCost
			player:sendStatus()
			player:playSound(7)
			player:sendMinitext("Kau merapal Static.")
			target:setDuration("static_mage", duration)
			target:sendAnimation(2, 0)
			target.paralyzed = true
		elseif (target.blType == BL_PC and player:canPK(target)) then
			player:sendMinitext("Ini tidak bisa dirapal pada orang lain.")
			return
		end
	end,
	while_cast = function(block)
		block.paralyzed = true
		block:sendStatus()
	end,
	recast = function(target)
		target.paralyzed = true
		target:sendStatus()
	end,
	uncast = function(block)
		block.paralyzed = false
		block:sendStatus()
	end,

	requirements = function(player)
		local level = 8
		local items = {"acorn", "soup_bowl", 0}
		local itemAmounts = {20, 1, 50}
		local desc = "Renders a target immobile."
		return level, items, itemAmounts, desc
	end
}
