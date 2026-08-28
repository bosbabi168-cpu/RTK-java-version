might_rogue = {
	cast = function(player)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(mights) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		player:sendMinitext("Kau merapal Might.")
		player:setDuration("might_rogue", 625000)
		player:playSound(12)
		player:sendAnimation(11)
		player:calcStat()
	end,
	recast = function(player)
		player.might = player.might + 3
		player:sendStatus()
	end,
	uncast = function(player)
		player.might = player.might - 3
		player:sendStatus()
	end,

	requirements = function(player)
		local level = 15
		local items = {Item("acorn").id, Item("snake_meat").id, 0}
		local itemAmounts = {50, 20, 100}
		local desc = "Temporarily increases your might."
		return level, items, itemAmounts, desc
	end
}

spirit_strength_rogue = {
	cast = function(player)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(mights) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		player:sendMinitext("Kau merapal Spirit Strength.")
		player:setDuration("spirit_strength_rogue", 625000)
		player:playSound(31)
		player:sendAnimation(117)
		player:calcStat()
	end,
	recast = function(player)
		player.might = player.might + 3
		player:sendStatus()
	end,
	uncast = function(player)
		player.might = player.might - 3
		player:sendStatus()
	end,

	requirements = function(player)
		local level = 15
		local items = {Item("acorn").id, Item("snake_meat").id, 0}
		local itemAmounts = {50, 20, 100}
		local desc = "Temporarily increases your might."
		return level, items, itemAmounts, desc
	end
}

inner_blessing_rogue = {
	cast = function(player)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(mights) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		player:sendMinitext("Kau merapal Inner Blessing.")
		player:setDuration("inner_blessing_rogue", 625000)
		player:playSound(31)
		player:sendAnimation(11)
		player:calcStat()
	end,
	recast = function(player)
		player.might = player.might + 3
		player:sendStatus()
	end,
	uncast = function(player)
		player.might = player.might - 3
		player:sendStatus()
	end,

	requirements = function(player)
		local level = 15
		local items = {Item("acorn").id, Item("snake_meat").id, 0}
		local itemAmounts = {50, 20, 100}
		local desc = "Temporarily increases your might."
		return level, items, itemAmounts, desc
	end
}

temper_rogue = {
	cast = function(player)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(mights) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		player:sendMinitext("Kau merapal Temper.")
		player:setDuration("temper_rogue", 625000)
		player:playSound(31)
		player:sendAnimation(11)
		player:calcStat()
	end,
	recast = function(player)
		player.might = player.might + 3
		player:sendStatus()
	end,
	uncast = function(player)
		player.might = player.might - 3
		player:sendStatus()
	end,

	requirements = function(player)
		local level = 15
		local items = {Item("acorn").id, Item("snake_meat").id, 0}
		local itemAmounts = {50, 20, 100}
		local desc = "Temporarily increases your might."
		return level, items, itemAmounts, desc
	end
}
