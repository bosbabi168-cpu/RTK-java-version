wolfs_fury_rogue = {
	cast = function(player)
		local magic = 30
		if (not player:canCast(1, 1, 0)) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(lesserFuries) or player.rage > 1 then
			player:sendMinitext("Mantra ini sudah aktif.")
			return
		end

		player.magic = player.magic - magic
		player:playSound(4)
		player:sendMinitext("Kau merapal Wolf's Fury.")
		player:setDuration("wolfs_fury_rogue", 625000)
		player:sendAnimation(11)
		player:sendAction(6, 35)
		player:calcStat()
	end,
	recast = function(player)
		player.rage = 2
		player:sendStatus()
	end,
	uncast = function(player)
		player.rage = 1
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 34
		local items = {Item("acorn").id, Item("light_fox_fur").id, 0}
		local itemAmounts = {150, 20, 300}
		local description = "A rage-filled assault."
		return level, items, itemAmounts, description
	end
}

souls_rage_rogue = {
	cast = function(player)
		local magic = 30
		if (not player:canCast(1, 1, 0)) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(lesserFuries) or player.rage > 1 then
			player:sendMinitext("Mantra ini sudah aktif.")
			return
		end

		player.magic = player.magic - magic
		player:playSound(4)
		player:sendMinitext("Kau merapal Soul's Rage.")
		player:setDuration("souls_rage_rogue", 625000)
		player:sendAnimation(103)
		player:sendAction(6, 35)
		player:calcStat()
	end,
	recast = function(player)
		player.rage = 2
		player:sendStatus()
	end,
	uncast = function(player)
		player.rage = 1
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 34
		local items = {Item("acorn").id, Item("light_fox_fur").id, 0}
		local itemAmounts = {150, 20, 300}
		local description = "A rage-filled assault."
		return level, items, itemAmounts, description
	end
}

spirit_of_the_forest_rogue = {
	cast = function(player)
		local magic = 30
		if (not player:canCast(1, 1, 0)) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(lesserFuries) or player.rage > 1 then
			player:sendMinitext("Mantra ini sudah aktif.")
			return
		end

		player.magic = player.magic - magic
		player:playSound(4)
		player:sendMinitext("Kau merapal Spirit of the Forest.")
		player:setDuration("spirit_of_the_forest_rogue", 625000)
		player:sendAnimation(106)
		player:sendAction(6, 35)
		player:calcStat()
	end,
	recast = function(player)
		player.rage = 2
		player:sendStatus()
	end,
	uncast = function(player)
		player.rage = 1
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 34
		local items = {Item("acorn").id, Item("light_fox_fur").id, 0}
		local itemAmounts = {150, 20, 300}
		local description = "A rage-filled assault."
		return level, items, itemAmounts, description
	end
}

augmentation_rogue = {
	cast = function(player)
		local magic = 30
		if (not player:canCast(1, 1, 0)) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(lesserFuries) or player.rage > 1 then
			player:sendMinitext("Mantra ini sudah aktif.")
			return
		end

		player.magic = player.magic - magic
		player:playSound(70)
		player:sendMinitext("Kau merapal Augmentation.")
		player:setDuration("augmentation_rogue", 625000)
		player:sendAnimation(59)
		player:sendAction(6, 35)
		player:calcStat()
	end,
	recast = function(player)
		player.rage = 2
		player:sendStatus()
	end,
	uncast = function(player)
		player.rage = 1
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 34
		local items = {Item("acorn").id, Item("light_fox_fur").id, 0}
		local itemAmounts = {150, 20, 300}
		local description = "A rage-filled assault."
		return level, items, itemAmounts, description
	end
}
